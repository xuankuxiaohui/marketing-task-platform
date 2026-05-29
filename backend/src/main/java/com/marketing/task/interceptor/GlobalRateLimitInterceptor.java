package com.marketing.task.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class GlobalRateLimitInterceptor implements HandlerInterceptor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rate-limit.client.max-requests:100}")
    private int clientMaxRequests;

    @Value("${rate-limit.client.window-seconds:1}")
    private int clientWindowSeconds;

    @Value("${rate-limit.admin.max-requests:200}")
    private int adminMaxRequests;

    @Value("${rate-limit.admin.window-seconds:1}")
    private int adminWindowSeconds;

    private final Cache<String, Deque<Long>> counters = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }

        String ip = getClientIp(request);
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

        if (!tryAcquire(key, maxRequests, window)) {
            log.warn("请求限流: ip={}, path={}, key={}", ip, path, key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = Result.fail(ErrorCode.RATE_LIMIT_EXCEEDED);
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }

        return true;
    }

    private boolean tryAcquire(String key, int maxRequests, Duration window) {
        Deque<Long> timestamps = counters.get(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
