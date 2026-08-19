package com.mkt.identity.application;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.mkt.identity.domain.AccountTypes;
import com.mkt.identity.domain.TokenPrefixes;
import com.mkt.identity.response.SessionView;
import com.mkt.identity.support.SessionUsernames;
import com.mkt.infra.session.KickReason;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    static final String IP_EXTRA = "ip";

    private final KickReasonStore kicks;

    public SessionService() {
        this(null);
    }

    @Autowired
    public SessionService(KickReasonStore kicks) {
        this.kicks = kicks;
    }

    public String loginAdmin(long userId, int maxConcurrent, String deviceId, String username) {
        return loginAdmin(userId, maxConcurrent, deviceId, username, null);
    }

    public String loginAdmin(long userId, int maxConcurrent, String deviceId, String username, String ip) {
        String raw = login(StpAdmin.LOGIC, userId, maxConcurrent, deviceId, username, ip);
        return TokenPrefixes.wrapAdmin(raw);
    }

    public String loginClient(long userId, int maxConcurrent, String deviceId, String username) {
        return loginClient(userId, maxConcurrent, deviceId, username, null);
    }

    public String loginClient(long userId, int maxConcurrent, String deviceId, String username, String ip) {
        String raw = login(StpClient.LOGIC, userId, maxConcurrent, deviceId, username, ip);
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

    public void kickAllAdmin(long userId) {
        kickAll(StpAdmin.LOGIC, userId);
    }

    public void kickAllClient(long userId) {
        kickAll(StpClient.LOGIC, userId);
    }

    public List<SessionView> listAdmin(Long userId) {
        return list(StpAdmin.LOGIC, AccountTypes.ADMIN, userId);
    }

    public List<SessionView> listClient(Long userId) {
        return list(StpClient.LOGIC, AccountTypes.PORTAL, userId);
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

    private static String login(
            StpLogic logic, long userId, int maxConcurrent, String deviceId, String username, String ip) {
        SaLoginParameter parameter = new SaLoginParameter()
                .setIsConcurrent(true)
                .setIsShare(false)
                .setMaxLoginCount(Math.max(1, maxConcurrent));
        if (deviceId != null && !deviceId.isBlank()) {
            parameter.setDeviceId(deviceId);
        }
        SessionUsernames.attach(parameter, username);
        if (ip != null && !ip.isBlank()) {
            parameter.setTerminalExtra(IP_EXTRA, ip);
        }
        String raw = logic.createLoginSession(userId, parameter);
        SessionUsernames.write(logic, userId, username);
        return raw;
    }

    private void kickAll(StpLogic logic, long userId) {
        List<String> tokens = logic.getTokenValueListByLoginId(userId);
        if (kicks != null && tokens != null) {
            for (String token : tokens) {
                if (token != null && !token.isBlank()) {
                    kicks.write(logic.getLoginType(), token, KickReason.ADMIN);
                }
            }
        }
        logic.kickout(userId);
    }

    private static List<SessionView> list(StpLogic logic, String accountType, Long userId) {
        List<SessionView> rows = new ArrayList<>();
        if (userId != null) {
            addTerminals(rows, logic, accountType, userId);
            return rows;
        }
        List<String> tokens = logic.searchTokenValue("", 0, -1, false);
        if (tokens == null || tokens.isEmpty()) {
            return rows;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String token : tokens) {
            Object loginId = logic.getLoginIdByToken(token);
            if (loginId == null) {
                continue;
            }
            String id = String.valueOf(loginId);
            if (id.isBlank() || "-4".equals(id) || "-5".equals(id) || "null".equals(id)) {
                continue;
            }
            if (seen.add(id)) {
                addTerminals(rows, logic, accountType, Long.parseLong(id));
            }
        }
        return rows;
    }

    private static void addTerminals(List<SessionView> rows, StpLogic logic, String accountType, long userId) {
        List<SaTerminalInfo> terminals = logic.getTerminalListByLoginId(userId);
        if (terminals == null) {
            return;
        }
        for (SaTerminalInfo terminal : terminals) {
            if (terminal == null || terminal.getTokenValue() == null || terminal.getTokenValue().isBlank()) {
                continue;
            }
            String raw = terminal.getTokenValue();
            String account = SessionUsernames.read(logic, raw, String.valueOf(userId));
            Instant loginAt = Instant.ofEpochMilli(terminal.getCreateTime());
            long lastActive = logic.getTokenLastActiveTime(raw);
            Instant lastActiveAt = lastActive > 0 ? Instant.ofEpochMilli(lastActive) : loginAt;
            String ip = text(terminal.getExtra(IP_EXTRA));
            String deviceId = blankToNull(terminal.getDeviceId());
            rows.add(new SessionView(last4(raw), account, accountType, loginAt, lastActiveAt, blankToNull(ip), deviceId));
        }
    }

    private static String last4(String token) {
        if (token.length() <= 4) {
            return token;
        }
        return token.substring(token.length() - 4);
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
