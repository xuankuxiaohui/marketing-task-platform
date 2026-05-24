package com.marketing.task.interceptor;

import com.marketing.task.common.BusinessException;
import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.security.AdminJwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private AdminJwtProvider adminJwtProvider;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private AuthProperties authProperties;

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    @Test
    void preHandle_shouldSetUserContextAndReturnTrue_whenValidBearerToken() throws Exception {
        authProperties = mockAuthProperties(false);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(adminJwtProvider.verifyAndGetUserId("valid-token")).thenReturn("admin-1");

        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(adminJwtProvider, authProperties);
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        UserContext ctx = UserContextHolder.get();
        assertNotNull(ctx);
        assertEquals("admin-1", ctx.getUserId());
    }

    @Test
    void preHandle_shouldReturn401_whenNoTokenAndMockDisabled() throws Exception {
        authProperties = mockAuthProperties(false);
        when(request.getHeader("Authorization")).thenReturn(null);
        PrintWriter writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);

        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(adminJwtProvider, authProperties);
        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");
        verify(writer).write(contains("401"));
    }

    @Test
    void preHandle_shouldSetMockContextAndReturnTrue_whenNoTokenAndMockEnabled() {
        authProperties = mockAuthProperties(true);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("mock-admin");

        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(adminJwtProvider, authProperties);
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        UserContext ctx = UserContextHolder.get();
        assertNotNull(ctx);
        assertEquals("mock-admin", ctx.getUserId());
    }

    @Test
    void afterCompletion_shouldClearUserContext() {
        UserContextHolder.set(UserContext.builder().userId("test").build());
        authProperties = mockAuthProperties(false);

        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(adminJwtProvider, authProperties);
        interceptor.afterCompletion(request, response, null, null);

        assertThrows(BusinessException.class, UserContextHolder::get);
    }

    private AuthProperties mockAuthProperties(boolean mockEnabled) {
        AuthProperties.JwtConfig admin = new AuthProperties.JwtConfig("secret", 120);
        AuthProperties.JwtConfig client = new AuthProperties.JwtConfig("secret", 120);
        return new AuthProperties(mockEnabled, admin, client);
    }
}
