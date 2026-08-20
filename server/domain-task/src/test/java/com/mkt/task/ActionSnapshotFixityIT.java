package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.domain.ActionMerger;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskStartResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R16.3: in-progress instance action merge reads the bound snapshot, not the edited definition. */
@Testcontainers
class ActionSnapshotFixityIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void laterActionEditDoesNotChangeBoundInstanceMerge() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            TaskDefinitionSaveCommand first = withActions(PublishITSupport.legal("act_fix"), null, "https://old.example/x");
            long taskId = env.publish(first);
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            TaskDetailResponse before = env.portal.detail(taskId, 9L, "WEB");
            assertThat(before.instanceId()).isEqualTo(started.instanceId());
            assertThat(before.currentStep().action().actionType()).isEqualTo("LINK");
            assertThat(before.currentStep().action().params()).containsEntry("url", "https://old.example/x");

            env.tx.executeWithoutResult(status -> env.defs.saveAggregate(
                    withActions(PublishITSupport.legal("act_fix"), taskId, "https://new.example/y")));
            env.tx.executeWithoutResult(
                    status -> env.publishes.publish(taskId, new PublishCommand(true, null)));

            TaskDetailResponse after = env.portal.detail(taskId, 9L, "WEB");
            assertThat(after.currentStep().action().params()).containsEntry("url", "https://old.example/x");
            assertThat(after.currentStep().action().actionType()).isNotEqualTo(ActionMerger.NONE);
        }
    }

    private static TaskDefinitionSaveCommand withActions(TaskDefinitionSaveCommand base, Long id, String url) {
        return new TaskDefinitionSaveCommand(
                id,
                base.code(),
                base.name(),
                base.description(),
                base.category(),
                base.iconUrl(),
                base.badgeText(),
                base.startTime(),
                base.endTime(),
                base.sortWeight(),
                base.cycleType(),
                base.cronExpr(),
                base.specialStart(),
                base.specialEnd(),
                base.mutexGroupId(),
                base.gray(),
                base.filter(),
                base.steps(),
                base.transitions(),
                List.of(new TaskActionCommand("TASK", null, "WEB", "LINK", Map.of("url", url), "去完成")));
    }
}
