package com.mkt.reward.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.PageData;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.application.ClaimAppService;
import com.mkt.reward.application.PrizePortalAppService;
import com.mkt.reward.response.ClaimResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PrizePortalControllerTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listAndClaimDelegate() {
        UserContext.set(new UserPrincipal(9L, "client", "u"));
        PrizePortalAppService portal = mock(PrizePortalAppService.class);
        ClaimAppService claims = mock(ClaimAppService.class);
        when(portal.list(9L, "PENDING", 1, 20)).thenReturn(new PageData<>(0, List.of()));
        when(claims.claim(4L, 9L)).thenReturn(new ClaimResponse("GRANTED", "ARRIVED"));
        @SuppressWarnings("unchecked")
        ObjectProvider<SlidingWindowRateLimiter> limiter = mock(ObjectProvider.class);
        when(limiter.getIfAvailable()).thenReturn(null);
        PrizePortalController controller = new PrizePortalController(portal, claims, limiter);
        assertThat(controller.list("PENDING", 1, 20).data().records()).isEmpty();
        assertThat(controller.claim(4L).data().status()).isEqualTo("GRANTED");
    }
}
