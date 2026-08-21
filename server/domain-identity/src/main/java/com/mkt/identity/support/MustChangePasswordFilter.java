package com.mkt.identity.support;

import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.application.PortalUserStore;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.Result;
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

/** R3.6 / R5.4: non-password writes are blocked until the user changes the initial/reset password. */
public final class MustChangePasswordFilter extends OncePerRequestFilter {

    private final SessionSide side;
    private final AdminUserStore admins;
    private final PortalUserStore portals;

    public MustChangePasswordFilter(SessionSide side, AdminUserStore admins, PortalUserStore portals) {
        this.side = side;
        this.admins = admins;
        this.portals = portals;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (safe(request.getMethod()) || allowedWrite(path(request), request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        UserPrincipal principal = UserContext.current().orElse(null);
        if (principal == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!mustChange(principal.userId())) {
            chain.doFilter(request, response);
            return;
        }
        write(response, AuthErrorCodes.PASSWORD_MUST_CHANGE);
    }

    private boolean mustChange(long userId) {
        if (side == SessionSide.ADMIN) {
            if (admins == null) {
                return false;
            }
            AdminUserEntity user = admins.getById(userId);
            return user != null && user.mustChangePasswordFlag();
        }
        if (portals == null) {
            return false;
        }
        PortalUserEntity user = portals.getById(userId);
        return user != null && user.mustChangePasswordFlag();
    }

    private static boolean safe(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean allowedWrite(String path, String method) {
        if ("PUT".equalsIgnoreCase(method) && passwordPath(path)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && logoutPath(path);
    }

    private boolean passwordPath(String path) {
        return side == SessionSide.ADMIN
                ? "/admin/auth/password".equals(path)
                : "/api/common/auth/password".equals(path);
    }

    private boolean logoutPath(String path) {
        return side == SessionSide.ADMIN
                ? "/admin/auth/logout".equals(path)
                : "/api/common/auth/logout".equals(path);
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
