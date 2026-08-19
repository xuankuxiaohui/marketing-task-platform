package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.it.RecordingTransactionManager;
import com.mkt.identity.it.TransactionalProxies;
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
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

/**
 * Goes through a Spring {@code @Transactional} proxy (not {@code new AdminAuthService}).
 * Five wrong passwords must commit lock + five FAILURE audits; do not swallow inside a TX.
 */
class AdminAuthServiceTransactionalTest {

    private final AdminUserStore users = Mockito.mock(AdminUserStore.class);
    private final CaptchaService captchas = Mockito.mock(CaptchaService.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final RecordingTransactionManager txm = new RecordingTransactionManager();
    private AdminAuthService service;
    private AdminUserEntity user;

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        PasswordHasher hasher = new PasswordHasher();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock);
        AdminAuthService target = new AdminAuthService(
                users,
                captchas,
                new AuthRateLimiter(limiter, (key, def) -> def),
                hasher,
                new SessionService(),
                new LoginAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                (key, def) -> def,
                clock);
        service = TransactionalProxies.proxy(target, txm);
        user = new AdminUserEntity();
        user.setId(1L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPasswordHash(hasher.hash("Abcdef12!x"));
        user.setStatus("ENABLED");
        user.setDeleted(0);
        user.setFailedAttempts(0);
        user.setMustChangePassword(0);
        when(users.getByUsername("alice")).thenReturn(user);
        when(users.listRoleCodes(1L)).thenReturn(List.of());
        when(users.listPermissionCodes(1L)).thenReturn(List.of());
        doNothing().when(captchas).consume(anyString(), anyString(), anyString());
        doAnswer(invocation -> {
                    LoginLock.State state = invocation.getArgument(1);
                    user.setFailedAttempts(state.failedAttempts());
                    user.setLockedUntil(IdentityTime.toUtc(state.lockedUntil()));
                    return null;
                })
                .when(users)
                .saveLock(eq(1L), any());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void fiveWrongPasswordsCommitLockAndFiveFailureAudits() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                            new AdminLoginCommand("alice", "wrongpass1!", "cid", "code", null),
                            new AuthAttemptContext("10.0.0.1", "ua", null))
                    .orThrow())
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(i == 4 ? AuthErrorCodes.LOGIN_LOCKED : AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
        }
        assertThat(txm.commits()).isEqualTo(5);
        assertThat(txm.rollbacks()).isZero();
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(LoginLock.of(user.getFailedAttempts(), IdentityTime.toInstant(user.getLockedUntil()))
                        .locked(clock.instant()))
                .isTrue();
        assertThat(outbox.all()).hasSize(5);
        for (var row : outbox.all()) {
            JsonNode payload = JsonUtil.readTree(row.payload());
            assertThat(payload.get("operatorId").isNull()).isTrue();
            assertThat(payload.get("result").asString()).isEqualTo("FAILURE");
        }
    }

    @Test
    void captchaErrorAndLockRejectCommitFailureAudit() {
        doAnswer(invocation -> {
                    throw new BusinessException(AuthErrorCodes.CAPTCHA_INVALID);
                })
                .when(captchas)
                .consume(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "Abcdef12!x", "cid", "bad", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.CAPTCHA_INVALID);
        user.setFailedAttempts(5);
        user.setLockedUntil(IdentityTime.toUtc(clock.instant().plusSeconds(60)));
        assertThatThrownBy(() -> service.login(
                        new AdminLoginCommand("alice", "Abcdef12!x", "cid", "bad", null),
                        new AuthAttemptContext("10.0.0.1", "ua", null))
                .orThrow())
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_LOCKED);
        assertThat(txm.commits()).isEqualTo(2);
        assertThat(txm.rollbacks()).isZero();
        assertThat(outbox.all()).hasSize(2);
        for (var row : outbox.all()) {
            JsonNode payload = JsonUtil.readTree(row.payload());
            assertThat(payload.get("operatorId").isNull()).isTrue();
            assertThat(payload.get("result").asString()).isEqualTo("FAILURE");
        }
    }
}
