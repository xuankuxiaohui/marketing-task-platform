package com.mkt.identity.application;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.event.EventCodes;
import com.mkt.identity.command.PortalLoginCommand;
import com.mkt.identity.command.PortalRegisterCommand;
import com.mkt.identity.config.ConfigService;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.DefaultNicknames;
import com.mkt.identity.domain.DeviceIds;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.domain.PasswordPolicies;
import com.mkt.identity.domain.Usernames;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.response.PortalAuthResponse;
import com.mkt.identity.response.UsernameAvailableResponse;
import com.mkt.identity.support.AuthConfigKeys;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(RiskCheckPort.class)
public class PortalAuthService {

    public static final String CAPTCHA_REALM = "portal";

    private final PortalUserStore users;
    private final CaptchaService captchas;
    private final AuthRateLimiter rateLimiter;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final RiskCheckPort risk;
    private final EventPublisher events;
    private final ConfigService configs;
    private final Clock clock;

    public PortalAuthService(
            PortalUserStore users,
            CaptchaService captchas,
            AuthRateLimiter rateLimiter,
            PasswordHasher hasher,
            SessionService sessions,
            RiskCheckPort risk,
            EventPublisher events,
            ConfigService configs,
            Clock clock) {
        this.users = users;
        this.captchas = captchas;
        this.rateLimiter = rateLimiter;
        this.hasher = hasher;
        this.sessions = sessions;
        this.risk = risk;
        this.events = events;
        this.configs = configs;
        this.clock = clock;
    }

    public UsernameAvailableResponse usernameAvailable(String raw, String ip) {
        rateLimiter.assertIpOnly(ip, AuthErrorCodes.LOGIN_RATE_LIMITED);
        String username = Usernames.normalize(raw);
        if (!Usernames.valid(username)) {
            return new UsernameAvailableResponse(false, "format");
        }
        if (users.usernameTaken(username)) {
            return new UsernameAvailableResponse(false, "duplicate");
        }
        return new UsernameAvailableResponse(true, null);
    }

    @Transactional
    public PortalAuthResponse register(PortalRegisterCommand command, AuthAttemptContext context) {
        String username = Usernames.normalize(command.username());
        rateLimiter.assertRegister(context.ip(), username);
        captchas.consume(CAPTCHA_REALM, command.captchaId(), command.captchaCode());
        if (!Usernames.valid(username)) {
            throw new BusinessException(AuthErrorCodes.USERNAME_INVALID_FORMAT);
        }
        if (!PasswordPolicies.portalSatisfied(command.password())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
        }
        String deviceId = DeviceIds.normalizeOrNull(context.deviceId());
        rejectIfBlocked(RiskScene.REGISTER, null, context.ip(), deviceId, AuthErrorCodes.RISK_BLOCKED_REGISTER);
        if (users.usernameTaken(username)) {
            throw new BusinessException(AuthErrorCodes.USERNAME_DUPLICATE);
        }
        Instant now = clock.instant();
        PortalUserEntity entity = new PortalUserEntity();
        entity.setUsername(username);
        entity.setNickname("用户");
        entity.setPasswordHash(hasher.hash(command.password()));
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        entity.setFailedAttempts(0);
        entity.setMustChangePassword(0);
        entity.setRegisteredAt(IdentityTime.toUtc(now));
        try {
            users.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(AuthErrorCodes.USERNAME_DUPLICATE, ex);
        }
        String nickname = DefaultNicknames.of(entity.getId());
        users.updateNickname(entity.getId(), nickname);
        entity.setNickname(nickname);
        int max = configs.getInt(AuthConfigKeys.PORTAL_MAX_CONCURRENT, AuthConfigKeys.DEFAULT_PORTAL_MAX_CONCURRENT);
        String token = sessions.loginClient(entity.getId(), max, deviceId, username);
        users.markLoginSuccess(entity.getId(), now);
        appendEvent(EventCodes.AUTH_REGISTER_SUCCESS, entity.getId(), context.ip(), deviceId);
        return new PortalAuthResponse(token, entity.getId(), nickname);
    }

    /**
     * Business rejection is a committed result so {@code failed_attempts} / {@code locked_until}
     * survive the proxy. Callers throw via {@link AuthAttempt#orThrow()} after return
     * (05-security §3.4; not REQUIRES_NEW).
     */
    @Transactional
    public AuthAttempt<PortalAuthResponse> login(PortalLoginCommand command, AuthAttemptContext context) {
        String username = Usernames.normalize(command.username());
        rateLimiter.assertLogin(context.ip(), username);
        Instant now = clock.instant();
        PortalUserEntity user = username == null ? null : users.getByUsername(username);
        try {
            if (user != null) {
                LoginLock.State lock = lockOf(user);
                if (lock.locked(now)) {
                    throw locked(lock, now);
                }
            }
            captchas.consume(CAPTCHA_REALM, command.captchaId(), command.captchaCode());
            String deviceId = DeviceIds.normalizeOrNull(context.deviceId());
            Long subjectId = user == null ? null : user.getId();
            rejectIfBlocked(RiskScene.LOGIN, subjectId, context.ip(), deviceId, AuthErrorCodes.RISK_BLOCKED_LOGIN);
            if (user == null) {
                throw new BusinessException(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
            }
            LoginLock.State lock = lockOf(user);
            if (lock.locked(now)) {
                throw locked(lock, now);
            }
            if (!hasher.matches(command.password(), user.getPasswordHash())) {
                LoginLock.State next = LoginLock.onFailure(lock, now);
                users.saveLock(user.getId(), next);
                if (next.locked(now)) {
                    throw locked(next, now);
                }
                throw new BusinessException(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
            }
            if (!user.enabled()) {
                throw new BusinessException(AuthErrorCodes.ACCOUNT_DISABLED);
            }
            int max = configs.getInt(AuthConfigKeys.PORTAL_MAX_CONCURRENT, AuthConfigKeys.DEFAULT_PORTAL_MAX_CONCURRENT);
            String token = sessions.loginClient(user.getId(), max, deviceId, user.getUsername());
            users.markLoginSuccess(user.getId(), now);
            appendEvent(EventCodes.AUTH_LOGIN_SUCCESS, user.getId(), context.ip(), deviceId);
            return AuthAttempt.ok(new PortalAuthResponse(token, user.getId(), user.getNickname()));
        } catch (BusinessException ex) {
            return AuthAttempt.rejected(ex);
        }
    }

    public void logout(String presentedToken) {
        sessions.logoutClient(presentedToken);
    }

    private void rejectIfBlocked(
            RiskScene scene, Long userId, String ip, String deviceId, AuthErrorCodes blocked) {
        var verdict = risk.check(scene, new RiskSubject(userId, ip, deviceId, null));
        if (verdict.action() == RiskAction.REJECT || verdict.action() == RiskAction.SILENT_REJECT) {
            throw new BusinessException(blocked);
        }
    }

    private static LoginLock.State lockOf(PortalUserEntity user) {
        return LoginLock.of(
                user.getFailedAttempts() == null ? 0 : user.getFailedAttempts(),
                IdentityTime.toInstant(user.getLockedUntil()));
    }

    private static BusinessException locked(LoginLock.State lock, Instant now) {
        return new BusinessException(
                AuthErrorCodes.LOGIN_LOCKED, "账号已锁定，请" + lock.remainingMinutes(now) + "分钟后重试");
    }

    private void appendEvent(String eventCode, long userId, String ip, String deviceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("ip", ip);
        payload.put("deviceId", deviceId);
        events.append(eventCode, "auth", String.valueOf(userId), payload);
    }
}
