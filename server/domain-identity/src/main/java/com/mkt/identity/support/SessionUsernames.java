package com.mkt.identity.support;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;

/** Username carried on Sa-Token terminal extra + account session (no extra table). */
public final class SessionUsernames {

    public static final String KEY = "username";

    private SessionUsernames() {
    }

    public static void attach(SaLoginParameter parameter, String username) {
        if (parameter == null || blank(username)) {
            return;
        }
        parameter.setTerminalExtra(KEY, username);
    }

    public static void write(StpLogic logic, Object loginId, String username) {
        if (logic == null || loginId == null || blank(username)) {
            return;
        }
        SaSession session = logic.getSessionByLoginId(loginId, false);
        if (session != null) {
            session.set(KEY, username);
        }
    }

    public static String read(StpLogic logic, String rawToken, String loginId) {
        if (logic == null) {
            return "";
        }
        if (rawToken != null && !rawToken.isBlank()) {
            SaTerminalInfo terminal = logic.getTerminalInfoByToken(rawToken);
            String fromTerminal = text(terminal == null ? null : terminal.getExtra(KEY));
            if (!fromTerminal.isEmpty()) {
                return fromTerminal;
            }
        }
        if (loginId == null || loginId.isBlank()) {
            return "";
        }
        SaSession session = logic.getSessionByLoginId(loginId, false);
        return text(session == null ? null : session.get(KEY));
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? "" : text;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
