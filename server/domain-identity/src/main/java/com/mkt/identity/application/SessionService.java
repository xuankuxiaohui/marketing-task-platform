package com.mkt.identity.application;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.mkt.identity.domain.TokenPrefixes;
import com.mkt.identity.support.SessionUsernames;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public String loginAdmin(long userId, int maxConcurrent, String deviceId, String username) {
        String raw = login(StpAdmin.LOGIC, userId, maxConcurrent, deviceId, username);
        return TokenPrefixes.wrapAdmin(raw);
    }

    public String loginClient(long userId, int maxConcurrent, String deviceId, String username) {
        String raw = login(StpClient.LOGIC, userId, maxConcurrent, deviceId, username);
        return TokenPrefixes.wrapClient(raw);
    }

    public void logoutAdmin(String presentedToken) {
        String raw = TokenPrefixes.unwrapAdmin(presentedToken);
        if (raw != null) {
            StpAdmin.LOGIC.logoutByTokenValue(raw);
        }
    }

    public void logoutClient(String presentedToken) {
        String raw = TokenPrefixes.unwrapClient(presentedToken);
        if (raw != null) {
            StpClient.LOGIC.logoutByTokenValue(raw);
        }
    }

    public void logoutAllAdmin(long userId) {
        StpAdmin.LOGIC.logout(userId);
    }

    public void logoutAllClient(long userId) {
        StpClient.LOGIC.logout(userId);
    }

    public void keepCurrentAdmin(long userId, String presentedToken) {
        keepCurrent(StpAdmin.LOGIC, userId, TokenPrefixes.unwrapAdmin(presentedToken));
    }

    public void keepCurrentClient(long userId, String presentedToken) {
        keepCurrent(StpClient.LOGIC, userId, TokenPrefixes.unwrapClient(presentedToken));
    }

    public boolean adminSessionValid(String rawToken) {
        return valid(StpAdmin.LOGIC, rawToken);
    }

    public boolean clientSessionValid(String rawToken) {
        return valid(StpClient.LOGIC, rawToken);
    }

    private static String login(StpLogic logic, long userId, int maxConcurrent, String deviceId, String username) {
        SaLoginParameter parameter = new SaLoginParameter()
                .setIsConcurrent(true)
                .setIsShare(false)
                .setMaxLoginCount(Math.max(1, maxConcurrent));
        if (deviceId != null && !deviceId.isBlank()) {
            parameter.setDeviceId(deviceId);
        }
        SessionUsernames.attach(parameter, username);
        String raw = logic.createLoginSession(userId, parameter);
        SessionUsernames.write(logic, userId, username);
        return raw;
    }

    private static void keepCurrent(StpLogic logic, long userId, String currentRaw) {
        List<String> tokens = logic.getTokenValueListByLoginId(userId);
        if (tokens == null) {
            return;
        }
        for (String token : tokens) {
            if (currentRaw == null || !currentRaw.equals(token)) {
                logic.logoutByTokenValue(token);
            }
        }
    }

    private static boolean valid(StpLogic logic, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Object loginId = logic.getLoginIdByToken(rawToken);
        if (loginId == null) {
            return false;
        }
        String id = String.valueOf(loginId);
        return !id.isBlank() && !"-4".equals(id) && !"-5".equals(id);
    }
}
