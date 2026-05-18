package com.marketing.task.interceptor;

import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.enums.Platform;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
            return Platform.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid X-Platform header: {}", value);
            return Platform.WEB;
        }
    }
}
