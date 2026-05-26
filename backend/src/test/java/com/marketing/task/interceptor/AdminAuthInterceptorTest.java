package com.marketing.task.interceptor;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.marketing.task.common.BusinessException;
import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private StpLogic clientStpLogic;

    private MockedStatic<StpUtil> stpUtilMock;

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
        if (stpUtilMock != null) {
            stpUtilMock.close();
        }
    }

    @Test
    void preHandle_shouldSetAdminMockContext_whenMockEnabledAndAdminPath() {
        AuthProperties authProperties = mockAuthProperties(true);
        when(request.getRequestURI()).thenReturn("/api/admin/tasks");
        when(request.getHeader("X-User-Id")).thenReturn("mock-admin");

        UserContextInterceptor interceptor = new UserContextInterceptor(authProperties, clientStpLogic);
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        UserContext ctx = UserContextHolder.get();
        assertNotNull(ctx);
        assertEquals("mock-admin", ctx.getUserId());
    }

    @Test
    void preHandle_shouldSetClientMockContext_whenMockEnabledAndClientPath() {
        AuthProperties authProperties = mockAuthProperties(true);
        when(request.getRequestURI()).thenReturn("/api/client/tasks");
        when(request.getHeader("X-User-Id")).thenReturn("mock-user");
        when(request.getHeader("X-User-Province")).thenReturn("BJ");
        when(request.getHeader("X-User-Role")).thenReturn("vip");
        when(request.getHeader("X-User-Tags")).thenReturn("vip,active");
        when(request.getHeader("X-User-Org-Id")).thenReturn("org_001");
        when(request.getHeader("X-User-Level")).thenReturn("5");
        when(request.getHeader("X-Platform")).thenReturn("WEB");

        UserContextInterceptor interceptor = new UserContextInterceptor(authProperties, clientStpLogic);
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        UserContext ctx = UserContextHolder.get();
        assertNotNull(ctx);
        assertEquals("mock-user", ctx.getUserId());
        assertEquals("BJ", ctx.getProvince());
        assertEquals("vip", ctx.getRole());
        assertEquals(5, ctx.getLevel());
    }

    @Test
    void preHandle_shouldReturnTrue_whenNoMockAndNoSaTokenLogin() {
        AuthProperties authProperties = mockAuthProperties(false);
        when(clientStpLogic.isLogin()).thenReturn(false);

        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        UserContextInterceptor interceptor = new UserContextInterceptor(authProperties, clientStpLogic);
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        // UserContext is NOT set when no auth is present in non-mock mode
        assertThrows(BusinessException.class, UserContextHolder::get);
    }

    @Test
    void afterCompletion_shouldClearUserContext() {
        UserContextHolder.set(UserContext.builder().userId("test").build());
        AuthProperties authProperties = mockAuthProperties(false);

        UserContextInterceptor interceptor = new UserContextInterceptor(authProperties, clientStpLogic);
        interceptor.afterCompletion(request, response, null, null);

        assertThrows(BusinessException.class, UserContextHolder::get);
    }

    private AuthProperties mockAuthProperties(boolean mockEnabled) {
        return new AuthProperties(mockEnabled, "admin-secret", "client-secret");
    }
}
