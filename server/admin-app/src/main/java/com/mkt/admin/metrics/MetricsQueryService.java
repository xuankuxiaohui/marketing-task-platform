package com.mkt.admin.metrics;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Reads mtr_* only (R23.5). Grain rollup and rates in process. */
public class MetricsQueryService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MetricsQueryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public FunnelResponse funnel(MetricsQuery query) {
        DateRange range = range(query);
        String sql = "SELECT day, dim_key, exposure_count, start_count, complete_count"
                + " FROM mtr_task_funnel_d WHERE day >= ? AND day < ?"
                + dimClause(query.dimKey())
                + " ORDER BY day, dim_key";
        List<Object> args = args(range, query.dimKey());
        List<MetricsRollup.DailyFunnel> rows = new ArrayList<>();
        jdbc.query(
                sql,
                (RowCallbackHandler) rs -> rows.add(new MetricsRollup.DailyFunnel(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("dim_key"),
                        rs.getLong("exposure_count"),
                        rs.getLong("start_count"),
                        rs.getLong("complete_count"))),
                args.toArray());
        return new FunnelResponse(MetricsRollup.funnel(rows, query.grain()));
    }

    public SpendMetricsResponse spend(MetricsQuery query) {
        DateRange range = range(query);
        String sql = "SELECT day, dim_key, arrived_count, arrived_cost_fen, sending_count, sending_cost_fen"
                + " FROM mtr_reward_spend_d WHERE day >= ? AND day < ?"
                + dimClause(query.dimKey())
                + " ORDER BY day, dim_key";
        List<Object> args = args(range, query.dimKey());
        List<MetricsRollup.DailySpend> rows = new ArrayList<>();
        jdbc.query(
                sql,
                (RowCallbackHandler) rs -> rows.add(new MetricsRollup.DailySpend(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("dim_key"),
                        rs.getLong("arrived_count"),
                        rs.getLong("arrived_cost_fen"),
                        rs.getLong("sending_count"),
                        rs.getLong("sending_cost_fen"))),
                args.toArray());
        return new SpendMetricsResponse(MetricsRollup.spend(rows, query.grain(), loadStock()));
    }

    public RiskMetricsResponse risk(MetricsQuery query) {
        DateRange range = range(query);
        String sql = "SELECT day, dim_key, hit_count, intercept_count"
                + " FROM mtr_risk_hit_d WHERE day >= ? AND day < ?"
                + dimClause(query.dimKey())
                + " ORDER BY day, dim_key";
        List<Object> args = args(range, query.dimKey());
        List<MetricsRollup.DailyRisk> rows = new ArrayList<>();
        jdbc.query(
                sql,
                (RowCallbackHandler) rs -> rows.add(new MetricsRollup.DailyRisk(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("dim_key"),
                        rs.getLong("hit_count"),
                        rs.getLong("intercept_count"))),
                args.toArray());
        return new RiskMetricsResponse(MetricsRollup.risk(rows, query.grain()));
    }

    public AdMetricsResponse ad(MetricsQuery query) {
        DateRange range = range(query);
        String dimSql = "";
        List<Object> args = args(range, null);
        if (query.dimKey() != null && !query.dimKey().isBlank()) {
            dimSql = " AND dim_key LIKE ?";
            args.add(query.dimKey().trim() + "%");
        }
        String sql = "SELECT day, dim_key, exposure_count, click_count"
                + " FROM mtr_ad_material_d WHERE day >= ? AND day < ?"
                + dimSql
                + " ORDER BY day, dim_key";
        List<MetricsRollup.DailyAd> rows = new ArrayList<>();
        jdbc.query(
                sql,
                (RowCallbackHandler) rs -> rows.add(new MetricsRollup.DailyAd(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("dim_key"),
                        rs.getLong("exposure_count"),
                        rs.getLong("click_count"))),
                args.toArray());
        return new AdMetricsResponse(MetricsRollup.ad(rows, query.grain()));
    }

    private Map<String, long[]> loadStock() {
        Map<String, long[]> stock = new LinkedHashMap<>();
        jdbc.query(
                "SELECT category_code, SUM(remaining_stock), SUM(total_stock)"
                        + " FROM rwd_prize WHERE deleted = 0 GROUP BY category_code",
                (RowCallbackHandler)
                        rs -> stock.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)}));
        return stock;
    }

    DateRange range(MetricsQuery query) {
        Instant from = query.from();
        Instant to = query.to();
        LocalDate today = clock.instant().atZone(MetricsAggregateService.SHANGHAI).toLocalDate();
        LocalDate fromDay;
        LocalDate toDayExclusive;
        if (from == null && to == null) {
            fromDay = today.minusDays(6);
            toDayExclusive = today.plusDays(1);
        } else {
            fromDay = from == null
                    ? today.minusDays(6)
                    : from.atZone(MetricsAggregateService.SHANGHAI).toLocalDate();
            if (to == null) {
                toDayExclusive = today.plusDays(1);
            } else {
                LocalDate toDay = to.atZone(MetricsAggregateService.SHANGHAI).toLocalDate();
                toDayExclusive = to.equals(toDay.atStartOfDay(MetricsAggregateService.SHANGHAI).toInstant())
                        ? toDay
                        : toDay.plusDays(1);
            }
        }
        return new DateRange(Date.valueOf(fromDay), Date.valueOf(toDayExclusive));
    }

    private static String dimClause(String dimKey) {
        if (dimKey == null || dimKey.isBlank()) {
            return "";
        }
        return " AND dim_key = ?";
    }

    private static List<Object> args(DateRange range, String dimKey) {
        List<Object> args = new ArrayList<>();
        args.add(range.from);
        args.add(range.toExclusive);
        if (dimKey != null && !dimKey.isBlank()) {
            args.add(dimKey.trim());
        }
        return args;
    }

    record DateRange(Date from, Date toExclusive) {}
}
