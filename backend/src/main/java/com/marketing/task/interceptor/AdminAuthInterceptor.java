package com.marketing.task.interceptor;

import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.security.AdminJwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final AdminJwtProvider adminJwtProvider;
    private final AuthProperties authProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String userId = adminJwtProvider.verifyAndGetUserId(token);
            UserContextHolder.set(UserContext.builder().userId(userId).platform(Platform.ADMIN).build());
            return true;
        }
        if (authProperties.mockEnabled()) {
            UserContextHolder.set(UserContext.builder()
                    .userId(request.getHeader("X-User-Id"))
                    .platform(Platform.ADMIN)
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
}
