package com.mkt.identity.support;

import com.mkt.identity.application.InternalHmacVerifier;
import com.mkt.identity.config.ConfigService;
import com.mkt.identity.domain.InternalHmacs;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.RateLimitedException;
import com.mkt.kernel.Result;
import com.mkt.kernel.json.JsonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** HMAC gate for {@code /internal/**} (design §4.8 / 05-security §7). */
public final class InternalAuthFilter extends OncePerRequestFilter {

    private final InternalHmacVerifier verifier;
    private final SlidingWindowRateLimiter limiter;
    private final ConfigService configs;

    public InternalAuthFilter(InternalHmacVerifier verifier, SlidingWindowRateLimiter limiter, ConfigService configs) {
        this.verifier = verifier;
        this.limiter = limiter;
        this.configs = configs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        if (!path.startsWith("/internal/") && !"/internal".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        try {
            String appId = verifier.authenticate(
                    request.getMethod(),
                    InternalHmacs.signedPath(request),
                    request.getHeader(InternalAuthConfigKeys.HEADER_APP_ID),
                    request.getHeader(InternalAuthConfigKeys.HEADER_TIMESTAMP),
                    request.getHeader(InternalAuthConfigKeys.HEADER_NONCE),
                    request.getHeader(InternalAuthConfigKeys.HEADER_SIGN),
                    body);
            assertRate(appId);
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } catch (BusinessException ex) {
            write(response, ex);
        }
    }

    private void assertRate(String appId) {
        if (limiter == null) {
            return;
        }
        int max = configs.getInt(
                InternalAuthConfigKeys.APP_RATE_PER_SECOND, InternalAuthConfigKeys.DEFAULT_APP_RATE_PER_SECOND);
        if (!limiter.tryAcquire(RateLimitDim.APP_ID, appId, 1, max)) {
            throw new RateLimitedException(CommonErrorCodes.RATE_LIMITED, 1);
        }
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri == null || uri.isEmpty() ? "/" : uri;
    }

    private static void write(HttpServletResponse response, BusinessException ex) throws IOException {
        ErrorCode code = ex.errorCode();
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (ex instanceof RateLimitedException limited && limited.retryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(limited.retryAfterSeconds()));
        }
        response.getWriter().write(JsonUtil.toJson(Result.fail(code, ex.getMessage())));
    }
}
