package com.mkt.task.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class TaskPortalControllerTest {

    private TaskPortalAppService portal;
    private TaskClaimAppService claims;
    private SlidingWindowRateLimiter limiter;
    private TaskPortalController controller;

    @BeforeEach
    void setUp() {
        portal = mock(TaskPortalAppService.class);
        claims = mock(TaskClaimAppService.class);
        limiter = mock(SlidingWindowRateLimiter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SlidingWindowRateLimiter> limiterProvider = mock(ObjectProvider.class);
        when(limiterProvider.getIfAvailable()).thenReturn(limiter);
        controller = new TaskPortalController(portal, claims, limiterProvider, new TaskSettings());
        UserContext.set(new UserPrincipal(9L, "client", "u9"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listDelegates() {
        when(portal.list(9L, null, 1, 20)).thenReturn(new PageData<>(0, List.of()));
        assertThat(controller.list(null, 1, 20).code()).isEqualTo(0);
    }

    @Test
    void startUsesRemoteAddrAndRateLimit() {
        when(limiter.tryAcquire(any(), anyString(), anyInt(), anyInt())).thenReturn(true);
        when(claims.start(anyLong(), anyLong(), anyString(), any(), any()))
                .thenReturn(new TaskStartResponse(3L, "IN_PROGRESS", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        assertThat(controller.start(4L, null, "WEB", request).data().instanceId()).isEqualTo(3L);
    }

    @Test
    void startRateLimited() {
        when(limiter.tryAcquire(any(), anyString(), anyInt(), anyInt())).thenReturn(false);
        HttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.start(4L, null, "WEB", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_RATE_LIMITED);
    }
}
