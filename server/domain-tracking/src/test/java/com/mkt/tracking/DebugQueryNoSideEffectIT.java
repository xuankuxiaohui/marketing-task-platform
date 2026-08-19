package com.mkt.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.tracking.command.TrackMetadataSaveCommand;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.it.TrackingITSupport;
import com.mkt.tracking.query.TrackDebugQuery;
import com.mkt.tracking.query.TrackMetadataQuery;
import com.mkt.tracking.response.TrackDebugEventResponse;
import com.mkt.tracking.response.TrackMetadataResponse;
import com.mkt.tracking.support.TrackErrorCodes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R29.1: debug query does not mutate {@code evt_event_log}; 1% sample of 10,000 rows returns
 * {@code ≤ 100 + pageSize}. Requires Docker; leave for CI.
 */
@Testcontainers
class DebugQueryNoSideEffectIT {

    private static final int ROWS = 10_000;
    private static final String EVENTS = "[{\"code\":\"page.view\",\"props\":{}}]";

    @Container
    static final MySQLContainer<?> MYSQL = TrackingITSupport.mysql();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void debugQueryLeavesEventLogUnchangedAndHonorsSampleRatio() {
        TrackingITSupport env = TrackingITSupport.start(MYSQL, Instant.parse("2026-08-19T12:00:00Z"));
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        env.jdbc.update("DELETE FROM evt_event_log");
        env.settings.setQuerySampleRatioPercent(1);
        insertClientRows(env, ROWS);

        Snapshot before = snapshot(env);
        assertThat(before.count()).isEqualTo(ROWS);

        PageQuery page = PageQuery.of(1, 100);
        PageData<TrackDebugEventResponse> result = env.debugQuery.query(
                new TrackDebugQuery(null, null, null, null, null, null, page));

        Snapshot after = snapshot(env);
        assertThat(after.count()).isEqualTo(before.count());
        assertThat(after.hash()).isEqualTo(before.hash());
        assertThat(result.total()).isLessThanOrEqualTo(100L + page.pageSize());
        assertThat(result.records()).hasSizeLessThanOrEqualTo(page.pageSize());
        assertThat(result.total()).isEqualTo(100);
        assertThat(result.records()).hasSize(100);
    }

    @Test
    void metadataSeedRoundTripDisableAndDuplicate() {
        TrackingITSupport env = TrackingITSupport.start(MYSQL, Instant.parse("2026-08-19T12:00:00Z"));
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        PageData<TrackMetadataResponse> seeds =
                env.metadataApp.page(new TrackMetadataQuery("page.view", null, PageQuery.of(1, 20)));
        assertThat(seeds.total()).isEqualTo(1);
        TrackMetadataResponse seed = seeds.records().get(0);
        assertThat(seed.status()).isEqualTo("ENABLED");
        assertThat(env.metadata.statusOf("page.view")).isEqualTo(MetadataStatus.ENABLED);

        Integer auditBefore = countAudit(env);
        env.tx.executeWithoutResult(status -> env.metadataApp.update(
                seed.id(),
                new TrackMetadataSaveCommand(
                        "page.view", seed.name(), seed.propSchema(), "DISABLED", seed.owner(), seed.remark())));
        assertThat(env.metadata.statusOf("page.view")).isEqualTo(MetadataStatus.DISABLED);
        assertThat(countAudit(env)).isEqualTo(auditBefore + 1);

        assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.metadataApp.create(
                        new TrackMetadataSaveCommand(
                                "page.view", "重复", null, "ENABLED", "tracking", null))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TrackErrorCodes.METADATA_DUPLICATE_CODE);
        assertThat(countAudit(env)).isEqualTo(auditBefore + 1);
    }

    private static void insertClientRows(TrackingITSupport env, int count) {
        String sql = "INSERT INTO evt_event_log"
                + " (id, source, event_code, user_id, device_id, events, batch_size, registered, simulated)"
                + " VALUES (?,?,?,?,?,?,1,1,0)";
        List<Object[]> batch = new ArrayList<>(500);
        for (int i = 1; i <= count; i++) {
            batch.add(new Object[] {i, "CLIENT", "page.view", 1L, "dev", EVENTS});
            if (batch.size() == 500) {
                env.jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            env.jdbc.batchUpdate(sql, batch);
        }
    }

    private static int countAudit(TrackingITSupport env) {
        Integer count = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox WHERE event_code='audit.log'", Integer.class);
        return count == null ? 0 : count;
    }

    private static Snapshot snapshot(TrackingITSupport env) {
        Long count = env.jdbc.queryForObject("SELECT COUNT(*) FROM evt_event_log", Long.class);
        Long hash = env.jdbc.queryForObject(
                "SELECT COALESCE(SUM(CRC32(CONCAT_WS('#', id, source, event_code,"
                        + " IFNULL(user_id,''), IFNULL(device_id,''),"
                        + " CAST(events AS CHAR), registered, simulated))), 0)"
                        + " FROM evt_event_log",
                Long.class);
        return new Snapshot(count == null ? 0 : count, hash == null ? 0 : hash);
    }

    private record Snapshot(long count, long hash) {}
}
