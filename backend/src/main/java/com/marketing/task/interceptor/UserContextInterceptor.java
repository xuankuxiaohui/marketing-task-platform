package com.marketing.task.interceptor;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.SaTokenUserContextBridge;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.enums.Platform;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;
    private final StpLogic clientStpLogic;

    public UserContextInterceptor(AuthProperties authProperties,
                                  @Qualifier("clientStpLogic") StpLogic clientStpLogic) {
        this.authProperties = authProperties;
        this.clientStpLogic = clientStpLogic;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Mock mode: read user context from X-User-* headers
        if (authProperties.mockEnabled()) {
            UserContext ctx = buildMockContext(request);
            UserContextHolder.set(ctx);
            return true;
        }

        // Real mode: read from Sa-Token session
        if (StpUtil.isLogin()) {
            UserContextHolder.set(SaTokenUserContextBridge.buildAdminUserContext());
            return true;
        }
        if (clientStpLogic.isLogin()) {
            UserContextHolder.set(SaTokenUserContextBridge.buildClientUserContext(clientStpLogic));
            return true;
        }

        return true; // Let SaInterceptor handle 401 if needed
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    private UserContext buildMockContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/admin/")) {
            return UserContext.builder()
                    .userId(request.getHeader("X-User-Id"))
                    .platform(Platform.ADMIN)
                    .build();
        }

        // Client mock
        String tagsHeader = request.getHeader("X-User-Tags");
        Set<String> tags = tagsHeader == null || tagsHeader.isBlank()
                ? Set.of()
                : Arrays.stream(tagsHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());

        return UserContext.builder()
                .userId(request.getHeader("X-User-Id"))
                .province(request.getHeader("X-User-Province"))
                .role(request.getHeader("X-User-Role"))
                .tags(tags)
                .orgId(request.getHeader("X-User-Org-Id"))
                .level(parseInt(request.getHeader("X-User-Level")))
                .platform(parsePlatform(request.getHeader("X-Platform")))
                .build();
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
