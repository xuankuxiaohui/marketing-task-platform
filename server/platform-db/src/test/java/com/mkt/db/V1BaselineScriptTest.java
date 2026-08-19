package com.mkt.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V1BaselineScriptTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = V1BaselineScriptTest.class.getResourceAsStream("/db/migration/V1__sys_baseline.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void createsExactlyTwelveSysTables() {
        Matcher matcher = Pattern.compile("CREATE TABLE (sys_[a-z_]+)").matcher(sql);
        java.util.List<String> tables = new java.util.ArrayList<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        assertThat(tables)
                .containsExactlyInAnyOrder(
                        "sys_admin_user",
                        "sys_role",
                        "sys_permission",
                        "sys_admin_user_role",
                        "sys_role_permission",
                        "sys_portal_user",
                        "sys_dict_type",
                        "sys_dict_entry",
                        "sys_config",
                        "sys_audit_log",
                        "sys_outbox",
                        "sys_internal_app");
    }

    @Test
    void outboxProducerAndAuditOperatorNullable() {
        assertThat(sql).contains("KEY idx_producer_status_next (producer, status, next_retry_at)");
        assertThat(sql).contains("CHECK (producer IN ('admin','portal'))");
        assertThat(sql).contains("operator_id      BIGINT       NULL");
    }

    @Test
    void charsetAndLogicalDeleteFollowSection31() {
        assertThat(sql).contains("utf8mb4_0900_ai_ci");
        assertThat(sql).contains("DATETIME(3)");
        assertThat(Pattern.compile("CREATE TABLE sys_admin_user[\\s\\S]+?deleted").matcher(sql).find()).isTrue();
        assertThat(Pattern.compile("CREATE TABLE sys_portal_user[\\s\\S]+?deleted").matcher(sql).find()).isTrue();
        assertThat(sql).doesNotContain("CREATE TABLE sys_role (\n  id          BIGINT AUTO_INCREMENT PRIMARY KEY,\n  deleted");
    }

    @Test
    void seedsMatchTask13Counts() {
        assertThat(count(sql, "\\('auth\\.|\\('ratelimit\\.|\\('task\\.|\\('reward\\.|\\('risk\\.|\\('track\\.|\\('ad\\.|\\('internal\\.|\\('metrics\\.|\\('signin\\.|\\('crowd\\.|\\('activity\\.|\\('retention\\."))
                .isEqualTo(52);
        assertThat(count(sql, "\\(6, '[^']+', '(home|mine|task-detail|prize-list|prize-detail|points|password|signin)'"))
                .isEqualTo(8);
        assertThat(sql).contains("'province', '省份'");
        assertThat(sql).contains("'user_level', '用户等级'");
        assertThat(sql).contains("'user_role', '用户角色'");
        assertThat(sql).contains("'user_tag', '用户标签'");
        assertThat(sql).contains("'task_category', '任务分类'");
        assertThat(sql).contains("'portal_route', '门户站内路由'");
        assertThat(count(sql, "'MENU', NULL")).isEqualTo(28);
        assertThat(sql).contains("'super-admin'");
        assertThat(sql).contains("must_change_password) VALUES\n(1, 'admin'");
        assertThat(sql).contains("track.disabled-event-policy");
        assertThat(sql).contains("'drop-count'");
        assertThat(sql).contains("reward.recon.auto-refulfill-enabled");
        assertThat(sql).contains("'false'");
    }

    private static int count(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
