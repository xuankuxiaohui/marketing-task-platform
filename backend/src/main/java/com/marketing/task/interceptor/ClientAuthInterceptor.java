package com.marketing.task.interceptor;

import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.security.ClientJwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientAuthInterceptor implements HandlerInterceptor {
    private final ClientJwtProvider clientJwtProvider;
    private final AuthProperties authProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            UserContext context = clientJwtProvider.verify(token);
            UserContextHolder.set(context);
            return true;
        }
        if (authProperties.mockEnabled()) {
            String tagsHeader = request.getHeader("X-User-Tags");
            Set<String> tags = tagsHeader == null || tagsHeader.isBlank()
                    ? Set.of()
                    : Arrays.stream(tagsHeader.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
            UserContextHolder.set(UserContext.builder()
                    .userId(request.getHeader("X-User-Id"))
                    .province(request.getHeader("X-User-Province"))
                    .role(request.getHeader("X-User-Role"))
                    .tags(tags)
                    .orgId(request.getHeader("X-User-Org-Id"))
                    .level(parseInt(request.getHeader("X-User-Level")))
                    .platform(parsePlatform(request.getHeader("X-Platform")))
                    .build());
            return true;
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":401,\"message\":\"未授权，请先登录\"}");
        } catch (java.io.IOException ignored) {
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.warn("Invalid X-User-Level header: {}", value);
            return null;
        }
    }

    private Platform parsePlatform(String value) {
        if (value == null || value.isBlank()) {
            return Platform.WEB;
        }
        try {
            return Platform.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid X-Platform header: {}", value);
            return Platform.WEB;
        }
    }
}
