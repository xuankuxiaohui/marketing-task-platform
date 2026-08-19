package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.TaskReadPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.contract.UserRiskSummary;
import com.mkt.identity.command.PortalUserProfileCommand;
import com.mkt.identity.command.PortalUserResetPasswordCommand;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PortalUserAppServiceTest {

    private final PortalUserMapper users = Mockito.mock(PortalUserMapper.class);
    private final SessionService sessions = Mockito.mock(SessionService.class);
    private final RewardPort rewards = Mockito.mock(RewardPort.class);
    private final RiskCheckPort risk = Mockito.mock(RiskCheckPort.class);
    private final TaskReadPort tasks = Mockito.mock(TaskReadPort.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PlatformCache cache;
    private PortalUserAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        UserAttributePortImpl attributes = new UserAttributePortImpl(users, cache);
        service = new PortalUserAppService(
                users,
                new PasswordHasher(),
                sessions,
                attributes,
                rewards,
                risk,
                tasks,
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void profileOverwriteReplacesTagsAndEvictsAttrCache() {
        PortalUserEntity user = liveUser(5L);
        when(users.selectById(5L)).thenReturn(user);
        when(users.updateById(any(PortalUserEntity.class))).thenReturn(1);
        cache.put(CacheNamespace.IDENTITY_USER_ATTR, "5", "stale");
        service.updateProfile(5L, new PortalUserProfileCommand("GD", "2", "vip", List.of("hot", "new"), "42"));
        assertThat(user.getProvince()).isEqualTo("GD");
        assertThat(user.getUserLevel()).isEqualTo("2");
        assertThat(user.getTags()).isEqualTo(JsonUtil.toJson(List.of("hot", "new")));
        assertThat(user.getOrgId()).isEqualTo("42");
        service.updateProfile(5L, new PortalUserProfileCommand("BJ", "3", null, List.of("only"), null));
        assertThat(JsonUtil.fromJson(user.getTags(), List.class)).containsExactly("only");
        assertThat(user.getOrgId()).isNull();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThat(cache.get(CacheNamespace.IDENTITY_USER_ATTR, "5", String.class, () -> "fresh"))
                .isEqualTo("fresh");
    }

    @Test
    void disableDeleteResetInvalidateSessions() {
        PortalUserEntity user = liveUser(5L);
        when(users.selectById(5L)).thenReturn(user);
        when(users.updateById(any(PortalUserEntity.class))).thenReturn(1);
        service.disable(5L);
        assertThat(user.getStatus()).isEqualTo("DISABLED");
        verify(sessions).logoutAllClient(5L);
        service.resetPassword(5L, new PortalUserResetPasswordCommand("pass1234"));
        assertThat(user.getMustChangePassword()).isEqualTo(1);
        verify(sessions, Mockito.times(2)).logoutAllClient(5L);
        service.delete(5L);
        assertThat(user.getDeleted()).isEqualTo(1);
        verify(sessions, Mockito.times(3)).logoutAllClient(5L);
    }

    @Test
    void detailAssemblesCrossDomainPorts() {
        PortalUserEntity user = liveUser(5L);
        when(users.selectById(5L)).thenReturn(user);
        when(rewards.userSummary(5L)).thenReturn(new UserRewardSummary(80L, new PrizeSummary(1L, 2L)));
        when(risk.userSummary(5L)).thenReturn(new UserRiskSummary(3L, List.of(RiskListType.BLACK)));
        when(tasks.instanceCounts(5L)).thenReturn(new InstanceCounts(4L, 9L));
        var detail = service.detail(5L);
        assertThat(detail.pointsBalance()).isEqualTo(80L);
        assertThat(detail.prizeSummary().won()).isEqualTo(1L);
        assertThat(detail.riskHits()).isEqualTo(3L);
        assertThat(detail.listStatus()).containsExactly(RiskListType.BLACK);
        assertThat(detail.inProgressInstanceCount()).isEqualTo(4L);
        assertThat(detail.historyInstanceCount()).isEqualTo(9L);
    }

    @Test
    void invalidOrgIdAndDeletedUserRejected() {
        PortalUserEntity user = liveUser(5L);
        when(users.selectById(5L)).thenReturn(user);
        assertThatThrownBy(() -> service.updateProfile(
                        5L, new PortalUserProfileCommand(null, null, null, List.of(), "bad org")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        user.setDeleted(1);
        assertThatThrownBy(() -> service.detail(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.NOT_FOUND);
        assertThatThrownBy(() -> service.resetPassword(5L, new PortalUserResetPasswordCommand("short")))
                .isInstanceOf(BusinessException.class);
        user.setDeleted(0);
        assertThatThrownBy(() -> service.resetPassword(5L, new PortalUserResetPasswordCommand("short")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
    }

    private static PortalUserEntity liveUser(long id) {
        PortalUserEntity entity = new PortalUserEntity();
        entity.setId(id);
        entity.setUsername("user_01");
        entity.setNickname("用户");
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        entity.setPasswordHash("hash");
        entity.setRegisteredAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }
}
