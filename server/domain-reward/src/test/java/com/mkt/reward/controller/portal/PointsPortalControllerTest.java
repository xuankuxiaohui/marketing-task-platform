package com.mkt.reward.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.kernel.PageData;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.response.PointsBalanceResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PointsPortalControllerTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void balanceAndListUseLoginUser() {
        UserContext.set(new UserPrincipal(9L, "client", "u"));
        PointsAppService points = mock(PointsAppService.class);
        when(points.balance(9L)).thenReturn(new PointsBalanceResponse(30L));
        when(points.pagePortal(9L, "EARN", 1, 20)).thenReturn(new PageData<>(0, List.of()));
        PointsPortalController controller = new PointsPortalController(points);
        assertThat(controller.balance().data().balance()).isEqualTo(30L);
        assertThat(controller.transactions("EARN", 1, 20).data().records()).isEmpty();
    }
}
