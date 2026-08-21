package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.admin.controller.admin.MetricsAdminController;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsAdminControllerTest {

    @Test
    void getEndpointsWrapQueryResults() {
        MetricsQueryService queries = mock(MetricsQueryService.class);
        when(queries.funnel(any())).thenReturn(new FunnelResponse(List.of()));
        when(queries.spend(any())).thenReturn(new SpendMetricsResponse(List.of()));
        when(queries.risk(any())).thenReturn(new RiskMetricsResponse(List.of()));
        when(queries.ad(any())).thenReturn(new AdMetricsResponse(List.of()));
        MetricsAdminController controller = new MetricsAdminController(queries);
        assertThat(controller.funnel(null, null, "DAY", null).success()).isTrue();
        assertThat(controller.spend(null, null, null, "POINTS").success()).isTrue();
        assertThat(controller.risk(null, null, "WEEK", "R-a").success()).isTrue();
        assertThat(controller.ad(null, null, "MONTH", "home").success()).isTrue();
    }
}
