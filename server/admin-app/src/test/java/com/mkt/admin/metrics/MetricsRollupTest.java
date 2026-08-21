package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsRollupTest {

    @Test
    void funnelDashWhenExposureZeroAndSumsWeek() {
        LocalDate mon = LocalDate.of(2026, 8, 17);
        LocalDate tue = LocalDate.of(2026, 8, 18);
        List<FunnelPointView> rows = MetricsRollup.funnel(
                List.of(
                        new MetricsRollup.DailyFunnel(mon, "1", 0, 2, 1),
                        new MetricsRollup.DailyFunnel(tue, "1", 10, 4, 2)),
                MetricsGrain.WEEK);
        assertThat(rows).hasSize(1);
        FunnelPointView row = rows.getFirst();
        assertThat(row.period()).isEqualTo("2026-W34");
        assertThat(row.exposureCount()).isEqualTo(10);
        assertThat(row.startCount()).isEqualTo(6);
        assertThat(row.completeCount()).isEqualTo(3);
        assertThat(row.startRate()).isCloseTo(0.6, within(1e-9));
        assertThat(row.completeRate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void funnelStartRateNullWhenNoExposure() {
        List<FunnelPointView> rows = MetricsRollup.funnel(
                List.of(new MetricsRollup.DailyFunnel(LocalDate.of(2026, 8, 20), "9", 0, 3, 1)),
                MetricsGrain.DAY);
        assertThat(rows.getFirst().startRate()).isNull();
        assertThat(rows.getFirst().completeRate()).isCloseTo(0.333333, within(1e-6));
    }

    @Test
    void spendAttachesStockAndAdCtrNull() {
        List<SpendPointView> spend = MetricsRollup.spend(
                List.of(new MetricsRollup.DailySpend(LocalDate.of(2026, 8, 1), "POINTS", 2, 10, 1, 4)),
                MetricsGrain.MONTH,
                Map.of("POINTS", new long[] {8, 20}));
        assertThat(spend.getFirst().period()).isEqualTo("2026-08");
        assertThat(spend.getFirst().remainingStock()).isEqualTo(8);
        assertThat(spend.getFirst().totalStock()).isEqualTo(20);
        List<AdPointView> ads = MetricsRollup.ad(
                List.of(new MetricsRollup.DailyAd(LocalDate.of(2026, 8, 20), "home:m1", 0, 3)),
                MetricsGrain.DAY);
        assertThat(ads.getFirst().ctr()).isNull();
        List<RiskPointView> risk = MetricsRollup.risk(
                List.of(new MetricsRollup.DailyRisk(LocalDate.of(2026, 8, 20), "R-a", 10, 4)),
                MetricsGrain.DAY);
        assertThat(risk.getFirst().interceptRate()).isCloseTo(0.4, within(1e-9));
    }
}
