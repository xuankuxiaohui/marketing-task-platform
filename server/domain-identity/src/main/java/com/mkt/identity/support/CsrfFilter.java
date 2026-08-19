package com.mkt.identity.support;

import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.Result;
import com.mkt.kernel.json.JsonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Double-submit CSRF for admin writes (R1.13). */
public final class CsrfFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if (isSafe(method)) {
            chain.doFilter(request, response);
            return;
        }
        String path = path(request);
        if (AnonymousPaths.adminAnonymous(method, path) || AnonymousPaths.isInfra(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (!path.startsWith("/admin")) {
            chain.doFilter(request, response);
            return;
        }
        String cookie = AuthCookies.read(request, AuthCookies.CSRF);
        String header = request.getHeader(AuthCookies.CSRF_HEADER);
        if (!CsrfTokens.equal(cookie, header)) {
            response.setStatus(CommonErrorCodes.PERMISSION_DENIED.httpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JsonUtil.toJson(Result.fail(CommonErrorCodes.PERMISSION_DENIED)));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSafe(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }
}
