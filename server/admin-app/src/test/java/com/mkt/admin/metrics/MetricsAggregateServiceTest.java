package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.config.ConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class MetricsAggregateServiceTest {

    @Test
    void upsertSqlCountsEventsExcludesSimulatedAndReplaces() {
        for (String sql :
                List.of(
                        MetricsAggregateService.FUNNEL_SQL,
                        MetricsAggregateService.SPEND_SQL,
                        MetricsAggregateService.RISK_SQL,
                        MetricsAggregateService.AD_SQL)) {
            assertThat(sql).contains("simulated = 0");
            assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
            assertThat(sql).doesNotContain("COUNT(DISTINCT");
        }
        assertThat(MetricsAggregateService.FUNNEL_SQL).contains("task.card.exposure");
        assertThat(MetricsAggregateService.FUNNEL_SQL).contains("JSON_TABLE");
        assertThat(MetricsAggregateService.SPEND_SQL).contains("ARRIVED");
        assertThat(MetricsAggregateService.RISK_SQL).contains("REJECTED");
        assertThat(MetricsAggregateService.AD_SQL).contains("ad.%.exposure");
    }

    @Test
    void runUpsertsThenPurgesWithClampedRetention() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.deleteFirst = 2;
        ConfigService configs = (key, def) -> "retention.metrics-days".equals(key) ? 10 : def;
        Instant now = Instant.parse("2026-08-20T04:00:00Z");
        int n = new MetricsAggregateService(jdbc, configs, Clock.fixed(now, ZoneOffset.UTC)).run(now);
        assertThat(n).isEqualTo(2);
        assertThat(jdbc.sqls.stream().filter(sql -> sql.startsWith("INSERT")).count()).isEqualTo(4);
        assertThat(jdbc.sqls.stream().anyMatch(sql -> sql.startsWith("DELETE FROM mtr_task_funnel_d"))).isTrue();
    }

    static final class RecordingJdbc extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        int deleteFirst;

        @Override
        public int update(String sql, Object... args) {
            sqls.add(sql);
            if (sql.startsWith("DELETE FROM") && deleteFirst > 0) {
                int value = deleteFirst;
                deleteFirst = 0;
                return value;
            }
            return 0;
        }

        @Override
        public void query(String sql, RowCallbackHandler rch, Object... args) {
            sqls.add(sql);
        }

        @Override
        public void query(String sql, RowCallbackHandler rch) {
            sqls.add(sql);
        }
    }
}
