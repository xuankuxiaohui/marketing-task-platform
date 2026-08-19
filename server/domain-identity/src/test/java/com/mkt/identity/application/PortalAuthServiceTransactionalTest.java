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
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskVerdict;
import com.mkt.identity.command.PortalLoginCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.it.RecordingTransactionManager;
import com.mkt.identity.it.TransactionalProxies;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PortalAuthServiceTransactionalTest {

    private final PortalUserStore users = Mockito.mock(PortalUserStore.class);
    private final CaptchaService captchas = Mockito.mock(CaptchaService.class);
    private final RiskCheckPort risk = Mockito.mock(RiskCheckPort.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final RecordingTransactionManager txm = new RecordingTransactionManager();
    private PortalAuthService service;
    private PortalUserEntity user;

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        PasswordHasher hasher = new PasswordHasher();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock);
        PortalAuthService target = new PortalAuthService(
                users,
                captchas,
                new AuthRateLimiter(limiter, (key, def) -> def),
                hasher,
                new SessionService(),
                risk,
                new EventPublisher(new MemoryOutboxStore(), OutboxProducer.PORTAL),
                (key, def) -> def,
                clock);
        service = TransactionalProxies.proxy(target, txm);
        user = new PortalUserEntity();
        user.setId(7L);
        user.setUsername("bob_01");
        user.setNickname("用户7");
        user.setPasswordHash(hasher.hash("abcdefg1"));
        user.setStatus("ENABLED");
        user.setDeleted(0);
        user.setFailedAttempts(0);
        when(users.getByUsername("bob_01")).thenReturn(user);
        when(risk.check(any(), any())).thenReturn(new RiskVerdict(RiskAction.PASS));
        doNothing().when(captchas).consume(anyString(), anyString(), anyString());
        doAnswer(invocation -> {
                    LoginLock.State state = invocation.getArgument(1);
                    user.setFailedAttempts(state.failedAttempts());
                    user.setLockedUntil(IdentityTime.toUtc(state.lockedUntil()));
                    return null;
                })
                .when(users)
                .saveLock(eq(7L), any());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void fiveWrongPasswordsCommitLock() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                            new PortalLoginCommand("bob_01", "wrongpass", "cid", "code"),
                            new AuthAttemptContext("10.0.0.2", "ua", null))
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
    }
}
