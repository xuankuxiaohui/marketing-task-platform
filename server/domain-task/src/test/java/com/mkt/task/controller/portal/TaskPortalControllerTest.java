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
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.ClientPlatformResolver;
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
    private TaskStepAppService steps;
    private TaskInstanceAppService instances;
    private SlidingWindowRateLimiter limiter;
    private TaskPortalController controller;

    @BeforeEach
    void setUp() {
        portal = mock(TaskPortalAppService.class);
        claims = mock(TaskClaimAppService.class);
        steps = mock(TaskStepAppService.class);
        instances = mock(TaskInstanceAppService.class);
        limiter = mock(SlidingWindowRateLimiter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SlidingWindowRateLimiter> limiterProvider = mock(ObjectProvider.class);
        when(limiterProvider.getIfAvailable()).thenReturn(limiter);
        controller = new TaskPortalController(
                portal, claims, steps, instances, limiterProvider, new TaskSettings(), new ClientPlatformResolver());
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

    @Test
    void clickDelegatesToStepService() {
        when(steps.click(anyLong(), anyString(), anyLong(), anyString(), any(), any()))
                .thenReturn(new TaskClickResponse(3L, "COMPLETED", "IN_PROGRESS", null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        assertThat(controller.click(3L, "a", null, "WEB", request).data().stepStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void abandonDelegatesToInstanceService() {
        when(instances.abandonUser(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new InstanceAbandonResponse(InstanceStatuses.ABANDONED));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        assertThat(controller.abandon(3L, null, request).data().instanceStatus())
                .isEqualTo(InstanceStatuses.ABANDONED);
    }
}
