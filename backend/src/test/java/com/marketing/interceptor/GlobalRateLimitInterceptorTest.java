package com.marketing.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalRateLimitInterceptorTest {

    private GlobalRateLimitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new GlobalRateLimitInterceptor();
        ReflectionTestUtils.setField(interceptor, "clientMaxRequests", 3);
        ReflectionTestUtils.setField(interceptor, "clientWindowSeconds", 1);
        ReflectionTestUtils.setField(interceptor, "adminMaxRequests", 5);
        ReflectionTestUtils.setField(interceptor, "adminWindowSeconds", 1);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void requestWithinLimit_shouldPass() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/client/tasks");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        for (int i = 0; i < 3; i++) {
            assertTrue(interceptor.preHandle(request, response, null));
        }
    }

    @Test
    void requestExceedingLimit_shouldReturn429() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/client/tasks");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 3; i++) {
            assertTrue(interceptor.preHandle(request, response, null));
        }

        assertFalse(interceptor.preHandle(request, response, null));
        verify(response).setStatus(429);
    }

    @Test
    void differentIps_shouldHaveSeparateLimits() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/client/tasks");

        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        for (int i = 0; i < 3; i++) {
            assertTrue(interceptor.preHandle(request, response, null));
        }

        when(request.getRemoteAddr()).thenReturn("10.0.0.3");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    void nonApiPath_shouldPass() throws Exception {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        assertTrue(interceptor.preHandle(request, response, null));
    }
}
