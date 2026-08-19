package com.mkt.identity.support;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpLogic;
import com.mkt.identity.domain.TokenPrefixes;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.session.KickReason;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.Result;
import com.mkt.kernel.SessionErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.json.JsonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Dual-namespace session filter (design §6.1 / D-02). */
public final class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionSide side;
    private final KickReasonStore kickReasons;
    private final SessionAvailability availability;

    public SessionAuthFilter(SessionSide side, KickReasonStore kickReasons, SessionAvailability availability) {
        this.side = side;
        this.kickReasons = kickReasons;
        this.availability = availability;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        String method = request.getMethod();
        boolean anonymous = side == SessionSide.ADMIN
                ? AnonymousPaths.adminAnonymous(method, path)
                : AnonymousPaths.portalAnonymous(method, path);
        boolean optional = side == SessionSide.PORTAL && AnonymousPaths.portalOptionalAuth(method, path);
        String presented = presentedToken(request);
        if (presented == null) {
            if (anonymous) {
                chain.doFilter(request, response);
                return;
            }
            write(response, side == SessionSide.ADMIN ? SessionErrorCodes.INVALID : SessionErrorCodes.MISSING);
            return;
        }
        if (side == SessionSide.ADMIN && TokenPrefixes.looksClient(presented)) {
            write(response, SessionErrorCodes.INVALID);
            return;
        }
        if (side == SessionSide.PORTAL && TokenPrefixes.looksAdmin(presented)) {
            write(response, SessionErrorCodes.MISSING);
            return;
        }
        if (anonymous && !optional) {
            // login/captcha/register: ignore kicked / expired / invalid same-side tokens
            chain.doFilter(request, response);
            return;
        }
        String raw = side == SessionSide.ADMIN
                ? TokenPrefixes.unwrapAdmin(presented)
                : TokenPrefixes.unwrapClient(presented);
        if (raw == null) {
            if (optional) {
                chain.doFilter(request, response);
                return;
            }
            write(response, side == SessionSide.ADMIN ? SessionErrorCodes.INVALID : SessionErrorCodes.MISSING);
            return;
        }
        if (!availability.available()) {
            if (optional) {
                chain.doFilter(request, response);
                return;
            }
            write(response, com.mkt.kernel.CommonErrorCodes.SERVER_ERROR);
            return;
        }
        StpLogic logic = side == SessionSide.ADMIN ? StpAdmin.LOGIC : StpClient.LOGIC;
        String loginType = logic.getLoginType();
        KickReason kick = optional
                ? kickReasons.peekQuiet(loginType, raw).orElse(null)
                : kickReasons.readAndDeleteQuiet(loginType, raw).orElse(null);
        Object loginId = logic.getLoginIdByToken(raw);
        String id = loginId == null ? null : String.valueOf(loginId);
        if (optional && (kick != null || sessionGone(id))) {
            chain.doFilter(request, response);
            return;
        }
        if (kick != null) {
            write(response, kickCode(kick));
            return;
        }
        if ("-4".equals(id)) {
            write(response, side == SessionSide.ADMIN
                    ? SessionErrorCodes.INVALID
                    : SessionErrorCodes.KICKED_CONCURRENT);
            return;
        }
        if ("-5".equals(id)) {
            write(response, side == SessionSide.ADMIN
                    ? SessionErrorCodes.INVALID
                    : SessionErrorCodes.KICKED_ADMIN);
            return;
        }
        if (id == null || id.isBlank() || "null".equals(id)) {
            write(response, side == SessionSide.ADMIN ? SessionErrorCodes.INVALID : SessionErrorCodes.EXPIRED);
            return;
        }
        try {
            bindToken(logic, raw);
            String username = SessionUsernames.read(logic, raw, id);
            UserContext.set(new UserPrincipal(Long.parseLong(id), loginType, username));
            logic.updateLastActiveToNow(raw);
            renew(logic, raw);
            slideAdminCookies(request, response, presented);
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void slideAdminCookies(HttpServletRequest request, HttpServletResponse response, String presented) {
        if (side != SessionSide.ADMIN || AuthCookies.read(request, AuthCookies.SESSION) == null) {
            return;
        }
        AuthCookies.writeSession(response, presented);
        String csrf = AuthCookies.read(request, AuthCookies.CSRF);
        if (csrf != null) {
            AuthCookies.writeCsrf(response, csrf);
        }
    }

    private static boolean sessionGone(String id) {
        return id == null || id.isBlank() || "null".equals(id) || "-4".equals(id) || "-5".equals(id);
    }

    private static void bindToken(StpLogic logic, String raw) {
        try {
            logic.setTokenValueToStorage(raw);
        } catch (SaTokenContextException ignored) {
            // no Sa servlet context; UserContext is the request principal
        }
    }

    private static void renew(StpLogic logic, String raw) {
        long timeout = logic.getConfigOrGlobal().getTimeout();
        if (timeout <= 0) {
            timeout = AuthCookies.MAX_AGE_SECONDS;
        }
        logic.renewTimeout(raw, timeout);
    }

    private String presentedToken(HttpServletRequest request) {
        if (side == SessionSide.ADMIN) {
            String cookie = AuthCookies.read(request, AuthCookies.SESSION);
            if (cookie != null) {
                return cookie;
            }
        }
        return AuthCookies.readBearer(request);
    }

    private ErrorCode kickCode(KickReason reason) {
        if (side == SessionSide.ADMIN) {
            return SessionErrorCodes.INVALID;
        }
        return switch (reason) {
            case CONCURRENT -> SessionErrorCodes.KICKED_CONCURRENT;
            case ADMIN -> SessionErrorCodes.KICKED_ADMIN;
        };
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private static void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(Result.fail(code)));
    }
}
