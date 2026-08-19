package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.identity.command.PortalLoginCommand;
import com.mkt.identity.command.PortalRegisterCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PortalAuthServiceTest {

    private final PortalUserStore users = Mockito.mock(PortalUserStore.class);
    private final CaptchaService captchas = Mockito.mock(CaptchaService.class);
    private final RiskCheckPort risk = Mockito.mock(RiskCheckPort.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private PortalAuthService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock);
        service = new PortalAuthService(
                users,
                captchas,
                new AuthRateLimiter(limiter, (key, def) -> def),
                new PasswordHasher(),
                new SessionService(),
                risk,
                new EventPublisher(outbox, OutboxProducer.PORTAL),
                (key, def) -> def,
                clock);
        doNothing().when(captchas).consume(anyString(), anyString(), anyString());
        when(risk.check(any(), any())).thenReturn(new RiskVerdict(RiskAction.PASS));
        when(users.usernameTaken(anyString())).thenReturn(false);
        AtomicLong ids = new AtomicLong(1001);
        doAnswer(invocation -> {
                    PortalUserEntity entity = invocation.getArgument(0);
                    entity.setId(ids.getAndIncrement());
                    return entity;
                })
                .when(users)
                .insert(any());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void registerAutoLogsInWithClientPrefix() {
        var response = service.register(
                new PortalRegisterCommand("bob_01", "abcdefg1", "cid", "code"),
                new AuthAttemptContext("10.0.0.2", "ua", "550e8400-e29b-41d4-a716-446655440000"));
        assertThat(response.token()).startsWith("client:");
        assertThat(response.nickname()).isEqualTo("用户1001");
        assertThat(outbox.all()).extracting(row -> row.eventCode()).contains("auth.register.success");
    }

    @Test
    void usernameAvailableReasons() {
        when(users.usernameTaken("bob_01")).thenReturn(true);
        assertThat(service.usernameAvailable("no", "1.1.1.1").reason()).isEqualTo("format");
        assertThat(service.usernameAvailable("bob_01", "1.1.1.1").reason()).isEqualTo("duplicate");
        when(users.usernameTaken("bob_02")).thenReturn(false);
        assertThat(service.usernameAvailable("bob_02", "1.1.1.1").available()).isTrue();
    }

    @Test
    void loginSuccessAndDisabledAndRisk() {
        PortalUserEntity user = new PortalUserEntity();
        user.setId(7L);
        user.setUsername("bob_01");
        user.setNickname("用户7");
        user.setPasswordHash(new PasswordHasher().hash("abcdefg1"));
        user.setStatus("ENABLED");
        user.setDeleted(0);
        user.setFailedAttempts(0);
        when(users.getByUsername("bob_01")).thenReturn(user);
        var ok = service.login(
                        new com.mkt.identity.command.PortalLoginCommand("bob_01", "abcdefg1", "cid", "code"),
                        new AuthAttemptContext("10.0.0.2", "ua", null))
                .orThrow();
        assertThat(ok.token()).startsWith("client:");
        user.setStatus("DISABLED");
        assertThatThrownBy(() -> service.login(
                        new com.mkt.identity.command.PortalLoginCommand("bob_01", "abcdefg1", "cid", "code"),
                        new AuthAttemptContext("10.0.0.2", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.ACCOUNT_DISABLED);
        when(risk.check(any(), any())).thenReturn(new RiskVerdict(RiskAction.REJECT));
        user.setStatus("ENABLED");
        assertThatThrownBy(() -> service.login(
                        new com.mkt.identity.command.PortalLoginCommand("bob_01", "abcdefg1", "cid", "code"),
                        new AuthAttemptContext("10.0.0.2", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.RISK_BLOCKED_LOGIN);
        service.logout(ok.token());
    }

    @Test
    void lockedAccountDoesNotConsumeCaptcha() {
        PortalUserEntity user = new PortalUserEntity();
        user.setId(7L);
        user.setUsername("bob_01");
        user.setPasswordHash(new PasswordHasher().hash("abcdefg1"));
        user.setStatus("ENABLED");
        user.setDeleted(0);
        user.setFailedAttempts(5);
        user.setLockedUntil(IdentityTime.toUtc(clock.instant().plusSeconds(60)));
        when(users.getByUsername("bob_01")).thenReturn(user);
        doThrow(new BusinessException(AuthErrorCodes.CAPTCHA_INVALID))
                .when(captchas)
                .consume(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.login(
                        new PortalLoginCommand("bob_01", "wrong", "cid", "bad"),
                        new AuthAttemptContext("10.0.0.2", "ua", null))
                .orThrow())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_LOCKED);
        verify(captchas, never()).consume(anyString(), anyString(), anyString());
    }

    @Test
    void riskRejectBlocksRegister() {
        when(risk.check(eqScene(), any())).thenReturn(new RiskVerdict(RiskAction.REJECT));
        assertThatThrownBy(() -> service.register(
                        new PortalRegisterCommand("bob_01", "abcdefg1", "cid", "code"),
                        new AuthAttemptContext("10.0.0.2", "ua", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.RISK_BLOCKED_REGISTER);
    }

    private static RiskScene eqScene() {
        return Mockito.eq(RiskScene.REGISTER);
    }
}
