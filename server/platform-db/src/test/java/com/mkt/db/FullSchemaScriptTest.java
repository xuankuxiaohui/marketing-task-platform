package com.mkt.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FullSchemaScriptTest {

    private static String v2;
    private static String v3;
    private static String v4;
    private static String v5;
    private static String v6;
    private static String all;

    @BeforeAll
    static void load() throws IOException {
        v2 = loadSql("V2__task_core.sql");
        v3 = loadSql("V3__reward_points.sql");
        v4 = loadSql("V4__risk_tracking.sql");
        v5 = loadSql("V5__sgn_signin.sql");
        v6 = loadSql("V6__act_activity.sql");
        all = v2 + "\n" + v3 + "\n" + v4 + "\n" + loadSql("V1__sys_baseline.sql");
    }

    @Test
    void createsThirtyNineTablesAcrossV1ToV4() {
        assertThat(tableNames(v2, "task_"))
                .containsExactlyInAnyOrder(
                        "task_definition",
                        "task_step",
                        "task_step_transition",
                        "task_platform_action",
                        "task_step_platform_action",
                        "task_mutex_group",
                        "task_crowd",
                        "task_crowd_item",
                        "task_version_snapshot",
                        "task_instance",
                        "task_instance_step",
                        "task_progress_report");
        assertThat(tableNames(v3, "rwd_"))
                .containsExactlyInAnyOrder(
                        "rwd_prize_category",
                        "rwd_prize_group",
                        "rwd_prize",
                        "rwd_grant_record",
                        "rwd_stock_log",
                        "rwd_recon_batch",
                        "rwd_recon_item");
        assertThat(tableNames(v3, "pnt_")).containsExactlyInAnyOrder("pnt_account", "pnt_transaction");
        assertThat(tableNames(v4, "risk_"))
                .containsExactlyInAnyOrder(
                        "risk_list_item", "risk_rule_config", "risk_hit_log", "risk_handle_log");
        assertThat(tableNames(v4, "evt_")).containsExactlyInAnyOrder("evt_event_log", "evt_event_metadata");
        assertThat(tableNames(all, "")).hasSize(39);
    }

    @Test
    void v5CreatesThreeSgnTablesWithoutTouchingV1ToV4() {
        assertThat(tableNames(v5, "sgn_"))
                .containsExactlyInAnyOrder("sgn_activity", "sgn_activity_snapshot", "sgn_record");
        assertThat(v5).contains("UNIQUE KEY uk_activity_user_date (activity_id, user_id, sign_date)");
        assertThat(v5).contains("UNIQUE KEY uk_activity_version (activity_id, version)");
        assertThat(v5).contains("CHECK (source IN ('CHECKIN','CATCHUP'))");
        assertThat(v5).contains("signin:config:query");
        assertThat(v5).contains("signin:record:query");
        assertThat(v5).contains("utf8mb4_0900_ai_ci");
        assertThat(v5.toLowerCase()).doesNotContain("foreign key");
        assertThat(v2 + v3 + v4).doesNotContain("CREATE TABLE sgn_");
    }

    @Test
    void v6CreatesTwoActTablesWithoutTouchingV1ToV5() {
        assertThat(tableNames(v6, "act_")).containsExactlyInAnyOrder("act_activity", "act_participation");
        assertThat(v6).contains("CHECK (result IN ('PASS','REJECT'))");
        assertThat(v6).contains("CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','OFFLINE'))");
        assertThat(v6).contains("activity:query");
        assertThat(v6).contains("activity:participation:query");
        assertThat(v6).contains("utf8mb4_0900_ai_ci");
        assertThat(v6.toLowerCase()).doesNotContain("foreign key");
        assertThat(v2 + v3 + v4 + v5).doesNotContain("CREATE TABLE act_");
    }

    @Test
    void uniqueIndexesAndNamedColumnsMatchSection3() {
        assertThat(v2).contains("UNIQUE KEY uk_user_task_cycle (user_id, task_id, cycle_key)");
        assertThat(v2).contains("UNIQUE KEY uk_dedup (instance_id, step_code, report_id)");
        assertThat(v2).contains("skip_reason      VARCHAR(32) NULL");
        assertThat(v2).contains("last_biz_no      VARCHAR(64) NULL");
        assertThat(v3).contains("UNIQUE KEY uk_idempotent (grant_source, source_id, prize_id)");
        assertThat(v3).contains("next_retry_at     DATETIME(3) NULL");
        assertThat(v3).contains("UNIQUE KEY uk_user (user_id)");
        assertThat(v4).contains("UNIQUE KEY uk_dim_type_value (dimension, list_type, list_value)");
        assertThat(v4).contains("UNIQUE KEY uk_event_code (event_code)");
        assertThat(extractPrizeCreate()).doesNotContainPattern("(?m)^\\s+version\\s+");
    }

    @Test
    void simulatedColumnOnFiveTables() {
        assertThat(v2).containsPattern("CREATE TABLE task_instance[\\s\\S]+?simulated\\s+TINYINT\\(1\\)");
        assertThat(v3).containsPattern("CREATE TABLE rwd_grant_record[\\s\\S]+?simulated\\s+TINYINT\\(1\\)");
        assertThat(v3).containsPattern("CREATE TABLE pnt_transaction[\\s\\S]+?simulated\\s+TINYINT\\(1\\)");
        assertThat(v4).containsPattern("CREATE TABLE risk_hit_log[\\s\\S]+?simulated\\s+TINYINT\\(1\\)");
        assertThat(v4).containsPattern("CREATE TABLE evt_event_log[\\s\\S]+?simulated\\s+TINYINT\\(1\\)");
    }

    @Test
    void categoryAndRiskSeeds() {
        assertThat(insertCodes(v3, "INSERT INTO rwd_prize_category"))
                .containsExactly(
                        "POINTS",
                        "ALIPAY_RED",
                        "WECHAT_RED",
                        "PHONE_CREDIT",
                        "COUPON",
                        "BADGE",
                        "PHYSICAL");
        assertThat(v3).contains("'ALIPAY_RED',   '支付宝红包', 'THIRD_PARTY', 'ASYNC',   'FACE_VALUE', 1, 'REVIEW'");
        assertThat(v3).contains("'WECHAT_RED',   '微信红包',   'THIRD_PARTY', 'ASYNC',   'FACE_VALUE', 1, 'REVIEW'");
        assertThat(v3).contains("'PHONE_CREDIT', '话费',     'THIRD_PARTY',  'ASYNC',   'FACE_VALUE', 1, 'REVIEW'");
        assertThat(insertCodes(v4, "INSERT INTO risk_rule_config"))
                .containsExactly("R-a", "R-b", "R-c", "R-d", "R-e", "R-f");
        assertThat(v4).contains("('R-e', 1, 5,  NULL,  'REJECT', 1)");
    }

    @Test
    void appendixDMetadataAndFirstMonthPartition() {
        assertThat(v4).contains("PARTITION BY RANGE COLUMNS(server_time)");
        assertThat(v4).contains("PARTITION p");
        assertThat(v4).contains("VALUES LESS THAN");
        assertThat(insertCodes(v4, "INSERT INTO evt_event_metadata"))
                .containsExactly(
                        "auth.register.success",
                        "auth.login.success",
                        "task.instance.start",
                        "task.step.complete",
                        "task.instance.complete",
                        "task.instance.abandon",
                        "task.instance.expire",
                        "risk.hit.recorded",
                        "page.view",
                        "page.leave",
                        "task.card.exposure",
                        "task.detail.view",
                        "task.start.click",
                        "task.step.click",
                        "task.complete.view",
                        "task.abandon.click",
                        "reward.grant.success",
                        "reward.grant.failed",
                        "reward.fulfill.arrived",
                        "reward.fulfill.failed",
                        "reward.claim.click",
                        "reward.list.view",
                        "points.page.view",
                        "signin.page.view",
                        "signin.sign.click",
                        "signin.catchup.click",
                        "ad.carousel.exposure",
                        "ad.splash.exposure",
                        "ad.popup.exposure",
                        "ad.float.exposure",
                        "ad.image.exposure",
                        "ad.carousel.click",
                        "ad.splash.click",
                        "ad.popup.click",
                        "ad.float.click",
                        "ad.image.click");
        assertThat(v4).doesNotContain("'audit.log'");
    }

    @Test
    void charsetAndNoPhysicalFk() {
        assertThat(v2).contains("utf8mb4_0900_ai_ci");
        assertThat(v3).contains("utf8mb4_0900_ai_ci");
        assertThat(v4).contains("utf8mb4_0900_ai_ci");
        assertThat(all.toLowerCase()).doesNotContain("foreign key");
        assertThat(all).doesNotContain("CONSTRAINT");
    }

    private static String extractPrizeCreate() {
        Matcher matcher = Pattern.compile("CREATE TABLE rwd_prize \\([\\s\\S]+?\\) COMMENT").matcher(v3);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private static List<String> tableNames(String sql, String prefix) {
        Matcher matcher = Pattern.compile("CREATE TABLE ([a-z_]+)").matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (prefix.isEmpty() || name.startsWith(prefix)) {
                tables.add(name);
            }
        }
        return tables;
    }

    private static List<String> insertCodes(String sql, String insertHeader) {
        int start = sql.indexOf(insertHeader);
        assertThat(start).as(insertHeader).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(';', start);
        assertThat(end).as(insertHeader + " terminator").isGreaterThan(start);
        String block = sql.substring(start, end);
        Matcher matcher = Pattern.compile("\\n\\('([^']+)'").matcher(block);
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static String loadSql(String name) throws IOException {
        try (InputStream in = FullSchemaScriptTest.class.getResourceAsStream("/db/migration/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
