package com.mkt.admin.metrics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory day → grain rollup. */
final class MetricsRollup {

    private MetricsRollup() {}

    static List<FunnelPointView> funnel(List<DailyFunnel> rows, MetricsGrain grain) {
        Map<String, long[]> rolled = new LinkedHashMap<>();
        Map<String, String> dims = new LinkedHashMap<>();
        for (DailyFunnel row : rows) {
            String period = MetricsPeriods.key(row.day(), grain);
            String key = period + "\0" + row.dimKey();
            long[] acc = rolled.computeIfAbsent(key, ignored -> new long[3]);
            acc[0] += row.exposureCount();
            acc[1] += row.startCount();
            acc[2] += row.completeCount();
            dims.putIfAbsent(key, row.dimKey());
        }
        List<FunnelPointView> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : rolled.entrySet()) {
            String period = entry.getKey().substring(0, entry.getKey().indexOf('\0'));
            long[] acc = entry.getValue();
            out.add(new FunnelPointView(
                    period,
                    dims.get(entry.getKey()),
                    acc[0],
                    acc[1],
                    acc[2],
                    MetricsRates.ratio(acc[1], acc[0]),
                    MetricsRates.ratio(acc[2], acc[1])));
        }
        return out;
    }

    static List<SpendPointView> spend(List<DailySpend> rows, MetricsGrain grain, Map<String, long[]> stock) {
        Map<String, long[]> rolled = new LinkedHashMap<>();
        Map<String, String> dims = new LinkedHashMap<>();
        for (DailySpend row : rows) {
            String period = MetricsPeriods.key(row.day(), grain);
            String key = period + "\0" + row.dimKey();
            long[] acc = rolled.computeIfAbsent(key, ignored -> new long[4]);
            acc[0] += row.arrivedCount();
            acc[1] += row.arrivedCostFen();
            acc[2] += row.sendingCount();
            acc[3] += row.sendingCostFen();
            dims.putIfAbsent(key, row.dimKey());
        }
        List<SpendPointView> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : rolled.entrySet()) {
            String period = entry.getKey().substring(0, entry.getKey().indexOf('\0'));
            String dim = dims.get(entry.getKey());
            long[] acc = entry.getValue();
            long[] levels = stock.getOrDefault(dim, new long[] {0, 0});
            out.add(new SpendPointView(
                    period, dim, acc[0], acc[1], acc[2], acc[3], levels[0], levels[1]));
        }
        return out;
    }

    static List<RiskPointView> risk(List<DailyRisk> rows, MetricsGrain grain) {
        Map<String, long[]> rolled = new LinkedHashMap<>();
        Map<String, String> dims = new LinkedHashMap<>();
        for (DailyRisk row : rows) {
            String period = MetricsPeriods.key(row.day(), grain);
            String key = period + "\0" + row.dimKey();
            long[] acc = rolled.computeIfAbsent(key, ignored -> new long[2]);
            acc[0] += row.hitCount();
            acc[1] += row.interceptCount();
            dims.putIfAbsent(key, row.dimKey());
        }
        List<RiskPointView> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : rolled.entrySet()) {
            String period = entry.getKey().substring(0, entry.getKey().indexOf('\0'));
            long[] acc = entry.getValue();
            out.add(new RiskPointView(
                    period,
                    dims.get(entry.getKey()),
                    acc[0],
                    acc[1],
                    MetricsRates.ratio(acc[1], acc[0])));
        }
        return out;
    }

    static List<AdPointView> ad(List<DailyAd> rows, MetricsGrain grain) {
        Map<String, long[]> rolled = new LinkedHashMap<>();
        Map<String, String> dims = new LinkedHashMap<>();
        for (DailyAd row : rows) {
            String period = MetricsPeriods.key(row.day(), grain);
            String key = period + "\0" + row.dimKey();
            long[] acc = rolled.computeIfAbsent(key, ignored -> new long[2]);
            acc[0] += row.exposureCount();
            acc[1] += row.clickCount();
            dims.putIfAbsent(key, row.dimKey());
        }
        List<AdPointView> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : rolled.entrySet()) {
            String period = entry.getKey().substring(0, entry.getKey().indexOf('\0'));
            long[] acc = entry.getValue();
            out.add(new AdPointView(
                    period,
                    dims.get(entry.getKey()),
                    acc[0],
                    acc[1],
                    MetricsRates.ratio(acc[1], acc[0])));
        }
        return out;
    }

    record DailyFunnel(LocalDate day, String dimKey, long exposureCount, long startCount, long completeCount) {}

    record DailySpend(
            LocalDate day,
            String dimKey,
            long arrivedCount,
            long arrivedCostFen,
            long sendingCount,
            long sendingCostFen) {}

    record DailyRisk(LocalDate day, String dimKey, long hitCount, long interceptCount) {}

    record DailyAd(LocalDate day, String dimKey, long exposureCount, long clickCount) {}
}
