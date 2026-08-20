package com.mkt.task.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryRewardPort;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskProgressReportStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** R14.3: INACTIVE → ACTIVE → COMPLETED/SKIPPED, never backwards. */
class StepStateMachinePropertyTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Property(tries = 80)
    void statusesOnlyMoveForward(
            @ForAll @IntRange(min = 1, max = 4) int stepCount,
            @ForAll @Size(min = 1, max = 12) List<@IntRange(min = 0, max = 3) Integer> ops) {
        MemoryTaskInstanceStore store = new MemoryTaskInstanceStore();
        MemoryTaskProgressReportStore reports = new MemoryTaskProgressReportStore();
        StepEngine engine = new StepEngine(
                store,
                reports,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TaskSettings(),
                new MemoryRewardPort());
        TaskInstanceEntity instance = insert(store);
        SnapshotContent snapshot = linearClicks(stepCount);
        engine.enter(instance, snapshot, attrs(), null);
        Map<String, String> previous = snapshotStatuses(store, instance.getId());
        assertLegal(previous.values());
        for (int op : ops) {
            List<TaskInstanceStepEntity> rows = store.listSteps(instance.getId());
            TaskInstanceStepEntity target = rows.get(Math.floorMod(op, rows.size()));
            try {
                engine.click(store.getById(instance.getId()), target, snapshot, attrs(), null);
            } catch (BusinessException ignored) {
                // illegal ops (INACTIVE / wrong step) must not mutate
            }
            Map<String, String> now = snapshotStatuses(store, instance.getId());
            assertLegal(now.values());
            for (TaskInstanceStepEntity row : rows) {
                String before = previous.get(row.getStepCode());
                String after = now.get(row.getStepCode());
                assertThat(rank(after)).isGreaterThanOrEqualTo(rank(before));
                if (StepStatuses.COMPLETED.equals(before) || StepStatuses.SKIPPED.equals(before)) {
                    assertThat(after).isEqualTo(before);
                }
            }
            long active = now.values().stream().filter(StepStatuses.ACTIVE::equals).count();
            assertThat(active).isLessThanOrEqualTo(1);
            previous = now;
        }
    }

    private static void assertLegal(Iterable<String> statuses) {
        for (String status : statuses) {
            assertThat(status)
                    .isIn(StepStatuses.INACTIVE, StepStatuses.ACTIVE, StepStatuses.COMPLETED, StepStatuses.SKIPPED);
        }
    }

    private static int rank(String status) {
        if (StepStatuses.INACTIVE.equals(status)) {
            return 0;
        }
        if (StepStatuses.ACTIVE.equals(status)) {
            return 1;
        }
        return 2;
    }

    private static Map<String, String> snapshotStatuses(MemoryTaskInstanceStore store, long instanceId) {
        Map<String, String> map = new HashMap<>();
        for (TaskInstanceStepEntity row : store.listSteps(instanceId)) {
            map.put(row.getStepCode(), row.getStatus());
        }
        return map;
    }

    private static TaskInstanceEntity insert(MemoryTaskInstanceStore store) {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(1L);
        row.setTaskCode("t");
        row.setVersion(1);
        row.setSnapshotId(1L);
        row.setUserId(9L);
        row.setCycleKey("NONE");
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC));
        row.setStartedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        row.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        store.insert(row);
        return row;
    }

    private static SnapshotContent linearClicks(int count) {
        List<TaskStepCommand> steps = new ArrayList<>();
        List<TaskTransitionCommand> edges = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String code = "s" + i;
            steps.add(new TaskStepCommand(code, code, i, "CLICK", null, null));
            if (i > 1) {
                edges.add(new TaskTransitionCommand("s" + (i - 1), code, null, 0));
            }
        }
        return new SnapshotContent(
                "t",
                "t",
                null,
                "daily",
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                0,
                "NONE",
                null,
                null,
                null,
                null,
                new TaskGrayCommand("NONE", null, null, null, null),
                new TaskFilterCommand(null, List.of(), List.of()),
                steps,
                edges,
                List.of());
    }

    private static UserAttributes attrs() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
