package com.mkt.admin.metrics;

import com.mkt.identity.config.ConfigService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;

/** Daily COUNT upsert from events / grants / hits. Re-run replaces, does not add (R23.1). */
public class MetricsAggregateService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    static final String FUNNEL_SQL =
            "INSERT INTO mtr_task_funnel_d (day, dim_key, exposure_count, start_count, complete_count)"
                    + " SELECT DATE(DATE_ADD(e.server_time, INTERVAL 8 HOUR)) AS d,"
                    + " JSON_UNQUOTE(JSON_EXTRACT(j.props, '$.taskId')) AS dim_key,"
                    + " SUM(j.code = 'task.card.exposure'),"
                    + " SUM(j.code = 'task.instance.start'),"
                    + " SUM(j.code = 'task.instance.complete')"
                    + " FROM evt_event_log e"
                    + " JOIN JSON_TABLE(e.events, '$[*]' COLUMNS ("
                    + "   code VARCHAR(64) PATH '$.code',"
                    + "   props JSON PATH '$.props'"
                    + " )) j"
                    + " WHERE e.simulated = 0 AND JSON_VALID(e.events)"
                    + " AND e.server_time >= ? AND e.server_time < ?"
                    + " AND j.code IN ('task.card.exposure','task.instance.start','task.instance.complete')"
                    + " GROUP BY d, dim_key"
                    + " HAVING dim_key IS NOT NULL AND dim_key <> '' AND dim_key <> 'null'"
                    + " ON DUPLICATE KEY UPDATE"
                    + " exposure_count = VALUES(exposure_count),"
                    + " start_count = VALUES(start_count),"
                    + " complete_count = VALUES(complete_count)";

    static final String SPEND_SQL =
            "INSERT INTO mtr_reward_spend_d (day, dim_key, arrived_count, arrived_cost_fen, sending_count, sending_cost_fen)"
                    + " SELECT DATE(DATE_ADD(granted_at, INTERVAL 8 HOUR)) AS d,"
                    + " category_code,"
                    + " SUM(CASE WHEN fulfillment_status = 'ARRIVED' THEN 1 ELSE 0 END),"
                    + " SUM(CASE WHEN fulfillment_status = 'ARRIVED' THEN cost_fen ELSE 0 END),"
                    + " SUM(CASE WHEN fulfillment_status = 'SENDING' THEN 1 ELSE 0 END),"
                    + " SUM(CASE WHEN fulfillment_status = 'SENDING' THEN cost_fen ELSE 0 END)"
                    + " FROM rwd_grant_record"
                    + " WHERE simulated = 0 AND fulfillment_status IN ('ARRIVED','SENDING')"
                    + " AND granted_at >= ? AND granted_at < ?"
                    + " GROUP BY d, category_code"
                    + " ON DUPLICATE KEY UPDATE"
                    + " arrived_count = VALUES(arrived_count),"
                    + " arrived_cost_fen = VALUES(arrived_cost_fen),"
                    + " sending_count = VALUES(sending_count),"
                    + " sending_cost_fen = VALUES(sending_cost_fen)";

    static final String RISK_SQL =
            "INSERT INTO mtr_risk_hit_d (day, dim_key, hit_count, intercept_count)"
                    + " SELECT DATE(DATE_ADD(occurred_at, INTERVAL 8 HOUR)) AS d,"
                    + " rule_code,"
                    + " COUNT(*),"
                    + " SUM(CASE WHEN action_result IN ('REJECTED','SILENT_REJECTED','FROZEN') THEN 1 ELSE 0 END)"
                    + " FROM risk_hit_log"
                    + " WHERE simulated = 0 AND occurred_at >= ? AND occurred_at < ?"
                    + " GROUP BY d, rule_code"
                    + " ON DUPLICATE KEY UPDATE"
                    + " hit_count = VALUES(hit_count),"
                    + " intercept_count = VALUES(intercept_count)";

    static final String AD_SQL =
            "INSERT INTO mtr_ad_material_d (day, dim_key, exposure_count, click_count)"
                    + " SELECT DATE(DATE_ADD(e.server_time, INTERVAL 8 HOUR)) AS d,"
                    + " CONCAT("
                    + "   IFNULL(JSON_UNQUOTE(JSON_EXTRACT(j.props, '$.positionCode')), ''),"
                    + "   ':',"
                    + "   IFNULL(JSON_UNQUOTE(JSON_EXTRACT(j.props, '$.materialTrackId')), '')"
                    + " ) AS dim_key,"
                    + " SUM(j.code LIKE 'ad.%.exposure'),"
                    + " SUM(j.code LIKE 'ad.%.click')"
                    + " FROM evt_event_log e"
                    + " JOIN JSON_TABLE(e.events, '$[*]' COLUMNS ("
                    + "   code VARCHAR(64) PATH '$.code',"
                    + "   props JSON PATH '$.props'"
                    + " )) j"
                    + " WHERE e.simulated = 0 AND JSON_VALID(e.events)"
                    + " AND e.server_time >= ? AND e.server_time < ?"
                    + " AND (j.code LIKE 'ad.%.exposure' OR j.code LIKE 'ad.%.click')"
                    + " GROUP BY d, dim_key"
                    + " HAVING dim_key IS NOT NULL AND dim_key <> ':' AND dim_key <> ''"
                    + " ON DUPLICATE KEY UPDATE"
                    + " exposure_count = VALUES(exposure_count),"
                    + " click_count = VALUES(click_count)";

    private final JdbcTemplate jdbc;
    private final ConfigService configs;
    private final Clock clock;

    public MetricsAggregateService(JdbcTemplate jdbc, ConfigService configs, Clock clock) {
        this.jdbc = jdbc;
        this.configs = configs;
        this.clock = clock;
    }

    public int run() {
        return run(clock.instant());
    }

    public int run(Instant now) {
        LocalDate today = now.atZone(SHANGHAI).toLocalDate();
        LocalDate fromDay = today.minusDays(MetricsConfigKeys.LOOKBACK_DAYS - 1L);
        Instant from = fromDay.atStartOfDay(SHANGHAI).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        Timestamp fromTs = Timestamp.from(from);
        Timestamp toTs = Timestamp.from(to);
        int n = 0;
        n += jdbc.update(FUNNEL_SQL, fromTs, toTs);
        n += jdbc.update(SPEND_SQL, fromTs, toTs);
        n += jdbc.update(RISK_SQL, fromTs, toTs);
        n += jdbc.update(AD_SQL, fromTs, toTs);
        n += purgeExpired(today);
        return n;
    }

    int purgeExpired(LocalDate today) {
        int days = configs.getInt(MetricsConfigKeys.RETENTION_DAYS, MetricsConfigKeys.DEFAULT_RETENTION_DAYS);
        if (days < 90) {
            days = 90;
        }
        Date cutoff = Date.valueOf(today.minusDays(days));
        int total = 0;
        total += deleteOlder("mtr_task_funnel_d", cutoff);
        total += deleteOlder("mtr_reward_spend_d", cutoff);
        total += deleteOlder("mtr_risk_hit_d", cutoff);
        total += deleteOlder("mtr_ad_material_d", cutoff);
        return total;
    }

    private int deleteOlder(String table, Date cutoff) {
        int total = 0;
        while (true) {
            int deleted = jdbc.update(
                    "DELETE FROM " + table + " WHERE day < ? LIMIT ?", cutoff, MetricsConfigKeys.CLEAN_BATCH);
            total += deleted;
            if (deleted < MetricsConfigKeys.CLEAN_BATCH) {
                return total;
            }
        }
    }
}
