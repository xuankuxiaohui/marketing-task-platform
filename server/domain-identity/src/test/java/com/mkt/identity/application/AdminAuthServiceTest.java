package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.command.ChangePasswordCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

class AdminAuthServiceTest {

    private final AdminUserStore users = Mockito.mock(AdminUserStore.class);
    private final CaptchaService captchas = Mockito.mock(CaptchaService.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private AdminAuthService service;
    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        hasher = new PasswordHasher();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock);
        AuthRateLimiter rates = new AuthRateLimiter(limiter, (key, def) -> def);
        service = new AdminAuthService(
                users,
                captchas,
                rates,
                hasher,
                new SessionService(),
                new LoginAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                (key, def) -> def,
                clock);
        doNothing().when(captchas).consume(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void successIssuesPrefixedTokenAndCsrf() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        when(users.getByUsername("alice")).thenReturn(user);
        when(users.listRoleCodes(1L)).thenReturn(List.of("super-admin"));
        when(users.listPermissionCodes(1L)).thenReturn(List.of("identity:admin-user:query"));

        AdminAuthService.IssuedAdminSession issued = service.login(
                        new AdminLoginCommand("alice", "Abcdef12!x", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow();

        assertThat(issued.token()).startsWith("admin:");
        assertThat(issued.body().csrfToken()).isEqualTo(issued.csrfToken());
        assertThat(issued.body().roles()).contains("super-admin");
        verify(users).markLoginSuccess(eq(1L), any());
        assertThat(outbox.all()).hasSize(1);
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("operatorId").asLong()).isEqualTo(1L);
        assertThat(payload.get("result").asString()).isEqualTo("SUCCESS");
    }

    @Test
    void wrongPasswordIsUnified() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        when(users.getByUsername("alice")).thenReturn(user);

        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "wrongpass1!", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
        verify(users).saveLock(eq(1L), any());
        verify(users, never()).markLoginSuccess(anyLong(), any());
    }

    @Test
    void fifthFailureLocks() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        user.setFailedAttempts(4);
        when(users.getByUsername("alice")).thenReturn(user);
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "wrongpass1!", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_LOCKED);
    }

    @Test
    void changePasswordKeepsCurrent() {
        AdminUserEntity user = user(9L, hasher.hash("OldPass12!x"));
        when(users.getById(9L)).thenReturn(user);
        when(users.getByUsername("alice")).thenReturn(user);
        when(users.listRoleCodes(9L)).thenReturn(List.of());
        when(users.listPermissionCodes(9L)).thenReturn(List.of());
        AdminAuthService.IssuedAdminSession first = service.login(
                        new AdminLoginCommand("alice", "OldPass12!x", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow();
        service.changePassword(9L, new ChangePasswordCommand("OldPass12!x", "NewPass12!x"), first.token());
        verify(users).updatePassword(eq(9L), anyString());
        assertThat(new SessionService().adminSessionValid(first.token().substring("admin:".length()))).isTrue();
    }

    @Test
    void failedLoginAuditHasNullOperatorIdAndJsonSummary() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        when(users.getByUsername("alice")).thenReturn(user);
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "wrongpass1!", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class);
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("operatorId").isNull()).isTrue();
        assertThat(payload.get("result").asString()).isEqualTo("FAILURE");
        assertThat(payload.get("operatorName").asString()).isEqualTo("alice");
        JsonNode summary = JsonUtil.readTree(payload.get("requestSummary").asString());
        assertThat(summary.get("username").asString()).isEqualTo("alice");
    }

    @Test
    void lockedAccountDoesNotConsumeCaptcha() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        user.setFailedAttempts(5);
        user.setLockedUntil(IdentityTime.toUtc(clock.instant().plusSeconds(60)));
        when(users.getByUsername("alice")).thenReturn(user);
        doThrow(new BusinessException(AuthErrorCodes.CAPTCHA_INVALID))
                .when(captchas)
                .consume(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "wrongpass1!", "cid", "bad", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_LOCKED);
        verify(captchas, never()).consume(anyString(), anyString(), anyString());
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("operatorId").isNull()).isTrue();
    }

    @Test
    void lockExpiryZerosAttemptsAndDoesNotImmediatelyRelock() {
        AdminUserEntity user = user(1L, hasher.hash("Abcdef12!x"));
        user.setFailedAttempts(5);
        user.setLockedUntil(IdentityTime.toUtc(clock.instant().minusSeconds(1)));
        when(users.getByUsername("alice")).thenReturn(user);
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "wrongpass1!", "cid", "code", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
        ArgumentCaptor<LoginLock.State> captor = ArgumentCaptor.forClass(LoginLock.State.class);
        verify(users).saveLock(eq(1L), captor.capture());
        LoginLock.State saved = captor.getValue();
        assertThat(saved.failedAttempts()).isEqualTo(1);
        assertThat(saved.locked(clock.instant())).isFalse();
    }

    private static AdminUserEntity user(long id, String hash) {
        AdminUserEntity entity = new AdminUserEntity();
        entity.setId(id);
        entity.setUsername("alice");
        entity.setNickname("Alice");
        entity.setPasswordHash(hash);
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        entity.setFailedAttempts(0);
        entity.setMustChangePassword(0);
        return entity;
    }
}
