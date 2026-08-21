package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class MetricsQueryJdbcTest {

    @Test
    void funnelReadsAggregateTableNotEvents() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getDate("day")).thenReturn(Date.valueOf("2026-08-20"));
            when(rs.getString("dim_key")).thenReturn("1");
            when(rs.getLong("exposure_count")).thenReturn(10L);
            when(rs.getLong("start_count")).thenReturn(4L);
            when(rs.getLong("complete_count")).thenReturn(2L);
            handler.processRow(rs);
            return null;
        })
                .when(jdbc)
                .query(contains("mtr_task_funnel_d"), any(RowCallbackHandler.class), any(Object[].class));
        MetricsQueryService queries =
                new MetricsQueryService(jdbc, Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC));
        FunnelResponse response = queries.funnel(new MetricsQuery(null, null, MetricsGrain.DAY, "1"));
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().exposureCount()).isEqualTo(10);
        assertThat(response.records().getFirst().completeRate()).isEqualTo(0.5);
    }
}
