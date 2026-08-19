package com.mkt.identity.application;

import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.command.ChangePasswordCommand;
import com.mkt.identity.config.ConfigService;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.DeviceIds;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.domain.PasswordPolicies;
import com.mkt.identity.domain.Usernames;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.response.AdminLoginResponse;
import com.mkt.identity.support.AuthConfigKeys;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.identity.support.CsrfTokens;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    public static final String CAPTCHA_REALM = "admin";

    private final AdminUserStore users;
    private final CaptchaService captchas;
    private final AuthRateLimiter rateLimiter;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final LoginAuditAppender audits;
    private final ConfigService configs;
    private final Clock clock;

    public AdminAuthService(
            AdminUserStore users,
            CaptchaService captchas,
            AuthRateLimiter rateLimiter,
            PasswordHasher hasher,
            SessionService sessions,
            LoginAuditAppender audits,
            ConfigService configs,
            Clock clock) {
        this.users = users;
        this.captchas = captchas;
        this.rateLimiter = rateLimiter;
        this.hasher = hasher;
        this.sessions = sessions;
        this.audits = audits;
        this.configs = configs;
        this.clock = clock;
    }

    @Transactional
    public IssuedAdminSession login(AdminLoginCommand command, AuthAttemptContext context) {
        String username = Usernames.normalize(command.username());
        rateLimiter.assertLogin(context.ip(), username);
        Instant now = clock.instant();
        AdminUserEntity user = username == null ? null : users.getByUsername(username);
        try {
            captchas.consume(CAPTCHA_REALM, command.captchaId(), command.captchaCode());
            AdminLoginResponse body = authenticate(user, command.password(), now);
            String csrf = CsrfTokens.create();
            int max = configs.getInt(AuthConfigKeys.ADMIN_MAX_CONCURRENT, AuthConfigKeys.DEFAULT_ADMIN_MAX_CONCURRENT);
            String token = sessions.loginAdmin(user.getId(), max, DeviceIds.normalizeOrNull(command.deviceId()));
            users.markLoginSuccess(user.getId(), now);
            audits.append(
                    "login",
                    user.getId(),
                    user.getUsername(),
                    "SUCCESS",
                    context.ip(),
                    context.userAgent(),
                    summary(username));
            return new IssuedAdminSession(
                    new AdminLoginResponse(
                            body.userId(),
                            body.nickname(),
                            body.roles(),
                            body.permissions(),
                            body.mustChangePassword(),
                            csrf),
                    token,
                    csrf);
        } catch (BusinessException ex) {
            audits.append(
                    "login",
                    user == null ? null : user.getId(),
                    submittedName(command.username()),
                    "FAILURE",
                    context.ip(),
                    context.userAgent(),
                    summary(username));
            throw ex;
        }
    }

    @Transactional
    public void logout(long userId, String username, String presentedToken, AuthAttemptContext context) {
        sessions.logoutAdmin(presentedToken);
        audits.append("logout", userId, username, "SUCCESS", context.ip(), context.userAgent(), "{}");
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordCommand command, String presentedToken) {
        if (!PasswordPolicies.adminSatisfied(command.newPassword())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
        }
        AdminUserEntity user = users.getById(userId);
        if (user == null || user.deletedFlag() || !hasher.matches(command.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_OLD_MISMATCH);
        }
        users.updatePassword(userId, hasher.hash(command.newPassword()));
        sessions.keepCurrentAdmin(userId, presentedToken);
        audits.append("password", userId, user.getUsername(), "SUCCESS", null, null, "{\"action\":\"change-password\"}");
    }

    private AdminLoginResponse authenticate(AdminUserEntity user, String password, Instant now) {
        if (user == null) {
            throw new BusinessException(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
        }
        LoginLock.State lock = LoginLock.of(
                user.getFailedAttempts() == null ? 0 : user.getFailedAttempts(),
                IdentityTime.toInstant(user.getLockedUntil()));
        if (lock.locked(now)) {
            throw new BusinessException(
                    AuthErrorCodes.LOGIN_LOCKED, "账号已锁定，请" + lock.remainingMinutes(now) + "分钟后重试");
        }
        if (!hasher.matches(password, user.getPasswordHash())) {
            LoginLock.State next = LoginLock.onFailure(lock, now);
            users.saveLock(user.getId(), next);
            if (next.locked(now)) {
                throw new BusinessException(
                        AuthErrorCodes.LOGIN_LOCKED, "账号已锁定，请" + next.remainingMinutes(now) + "分钟后重试");
            }
            throw new BusinessException(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
        }
        if (!user.enabled()) {
            throw new BusinessException(AuthErrorCodes.ACCOUNT_DISABLED);
        }
        List<String> roles = users.listRoleCodes(user.getId());
        List<String> permissions = users.listPermissionCodes(user.getId());
        return new AdminLoginResponse(
                user.getId(),
                user.getNickname(),
                roles,
                permissions,
                user.mustChangePasswordFlag(),
                "");
    }

    private static String submittedName(String username) {
        if (username == null) {
            return "";
        }
        return username.length() <= 30 ? username : username.substring(0, 30);
    }

    private static String summary(String username) {
        return "{\"username\":\"" + (username == null ? "" : username) + "\"}";
    }

    public record IssuedAdminSession(AdminLoginResponse body, String token, String csrfToken) {}
}
