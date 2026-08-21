package com.mkt.reward.application;

import com.mkt.kernel.json.JsonUtil;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

/**
 * Application-layer scan of {@code task_version_snapshot.content} (R17.1 / R17.7).
 * Does not import task Mapper/Entity (RL-03).
 */
@Repository
public class SnapshotPrizeScanner implements SnapshotPrizeLookup {

    private final JdbcTemplate jdbc;

    public SnapshotPrizeScanner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int affectedPublishedTaskCount(long prizeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT s.content
                FROM task_definition d
                JOIN task_version_snapshot s ON s.task_id = d.id AND s.version = d.version
                WHERE d.status = 'PUBLISHED' AND d.deleted = 0 AND d.version > 0
                """);
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (contentReferencesPrize(stringify(row.get("content")), prizeId)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean referencedByAnySnapshot(long prizeId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT content FROM task_version_snapshot");
        for (Map<String, Object> row : rows) {
            if (contentReferencesPrize(stringify(row.get("content")), prizeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int inFlightInstanceCount(long prizeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT s.content, i.id AS instance_id
                FROM task_instance i
                JOIN task_version_snapshot s ON s.id = i.snapshot_id
                WHERE i.status = 'IN_PROGRESS'
                """);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String content = stringify(row.get("content"));
            Object instanceId = row.get("instance_id");
            if (instanceId == null || !contentReferencesPrize(content, prizeId)) {
                continue;
            }
            List<String> stepCodes = rewardStepCodes(content, prizeId);
            if (stepCodes.isEmpty()) {
                continue;
            }
            Long unfinished = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM task_instance_step
                    WHERE instance_id = ?
                      AND step_code IN (%s)
                      AND status NOT IN ('COMPLETED', 'SKIPPED')
                    """
                            .formatted(placeholders(stepCodes.size())),
                    Long.class,
                    args(instanceId, stepCodes));
            if (unfinished != null && unfinished > 0) {
                count++;
            }
        }
        return count;
    }

    static boolean contentReferencesPrize(String content, long prizeId) {
        return !rewardStepCodes(content, prizeId).isEmpty();
    }

    static List<String> rewardStepCodes(String content, long prizeId) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        JsonNode root = JsonUtil.readTree(content);
        JsonNode steps = root.get("steps");
        if (steps == null || !steps.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> codes = new java.util.ArrayList<>();
        for (JsonNode step : steps) {
            JsonNode type = step.get("type");
            JsonNode prize = step.get("prizeId");
            JsonNode code = step.get("code");
            if (type != null
                    && "REWARD".equals(type.asString())
                    && prize != null
                    && prize.isNumber()
                    && prize.asLong() == prizeId
                    && code != null
                    && code.asString() != null) {
                codes.add(code.asString());
            }
        }
        return List.copyOf(codes);
    }

    private static String stringify(Object value) {
        return value == null ? null : value.toString();
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static Object[] args(Object instanceId, List<String> stepCodes) {
        Object[] values = new Object[1 + stepCodes.size()];
        values[0] = instanceId;
        for (int i = 0; i < stepCodes.size(); i++) {
            values[i + 1] = stepCodes.get(i);
        }
        return values;
    }
}
