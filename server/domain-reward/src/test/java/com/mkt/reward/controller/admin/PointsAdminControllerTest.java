package com.mkt.reward.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.kernel.PageData;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.response.PointsBalanceResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointsAdminControllerTest {

    @Test
    void endpointsDelegate() {
        PointsAppService points = mock(PointsAppService.class);
        when(points.pageAccounts(any())).thenReturn(new PageData<>(0, List.of()));
        when(points.adjust(any())).thenReturn(new PointsBalanceResponse(12L));
        when(points.pageTransactions(any())).thenReturn(new PageData<>(0, List.of()));
        PointsAdminController controller = new PointsAdminController(points);
        assertThat(controller.accounts(9L, 1, 20).data().total()).isZero();
        assertThat(controller.adjust(new PointsAdjustCommand(9L, 12L, "gift")).data().balance())
                .isEqualTo(12L);
        assertThat(controller.transactions(9L, "EARN", null, null, 1, 20).data().records())
                .isEmpty();
    }
}
