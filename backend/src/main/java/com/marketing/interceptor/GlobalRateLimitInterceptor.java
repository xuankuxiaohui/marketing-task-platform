package com.marketing.interceptor;

import com.marketing.common.ErrorCode;
import com.marketing.common.Result;
import com.marketing.utils.IpUtils;
import com.marketing.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
public class GlobalRateLimitInterceptor implements HandlerInterceptor {

    @Value("${rate-limit.client.max-requests:100}")
    private int clientMaxRequests;

    @Value("${rate-limit.client.window-seconds:1}")
    private int clientWindowSeconds;

    @Value("${rate-limit.admin.max-requests:200}")
    private int adminMaxRequests;

    @Value("${rate-limit.admin.window-seconds:1}")
    private int adminWindowSeconds;

    private final RateLimiter rateLimiter;

    public GlobalRateLimitInterceptor(
            @Qualifier("localRateLimiter") RateLimiter localRateLimiter,
            @Qualifier("redisRateLimiter") RateLimiter redisRateLimiter,
            @Value("${rate-limit.type:local}") String rateLimitType) {
        this.rateLimiter = "redis".equalsIgnoreCase(rateLimitType) ? redisRateLimiter : localRateLimiter;
        log.info("Rate limiter initialized with type: {}", rateLimitType);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }

        String ip = IpUtils.getRealIpAddr(request);
        String key;
        int maxRequests;
        Duration window;

        if (path.startsWith("/api/client/")) {
            key = "client:" + ip;
            maxRequests = clientMaxRequests;
            window = Duration.ofSeconds(clientWindowSeconds);
        } else if (path.startsWith("/api/admin/")) {
            key = "admin:" + ip;
            maxRequests = adminMaxRequests;
            window = Duration.ofSeconds(adminWindowSeconds);
        } else {
            return true;
        }

        if (!rateLimiter.tryAcquire(key, maxRequests, window)) {
            log.warn("请求限流: ip={}, path={}, key={}", ip, path, key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = Result.fail(ErrorCode.RATE_LIMIT_EXCEEDED);
            response.getWriter().write(JsonUtil.objToJson(result));
            return false;
        }

        return true;
    }
}