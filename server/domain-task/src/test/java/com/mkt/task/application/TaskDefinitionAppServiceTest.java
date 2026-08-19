package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryTaskChildStore;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskDefinitionAppServiceTest {

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskChildStore children;
    private TaskDefinitionAppService service;
    private TaskSettings settings;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        children = new MemoryTaskChildStore();
        settings = new TaskSettings();
        settings.setStepMaxCount(3);
        service = new TaskDefinitionAppService(
                definitions,
                children,
                new MemoryTaskMutexGroupStore(),
                new MemoryTaskCrowdStore(),
                settings,
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void saveHappyPathPersistsAggregate() {
        var saved = service.saveAggregate(legal());
        assertThat(saved.id()).isPositive();
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(definitions.liveCount()).isEqualTo(1);
        assertThat(children.listSteps(saved.id())).hasSize(2);
        assertThat(children.listTransitions(saved.id())).hasSize(1);
    }

    @Test
    void stepCountExceededLeavesNoRows() {
        List<TaskStepCommand> steps = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            steps.add(new TaskStepCommand("s" + i, "n" + i, i, "CLICK", null, null));
        }
        TaskDefinitionSaveCommand cmd = withSteps(legal(), steps, List.of());
        assertThatThrownBy(() -> service.saveAggregate(cmd))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_COUNT_EXCEEDED);
        assertThat(definitions.liveCount()).isZero();
        assertThat(children.steps).isEmpty();
    }

    @Test
    void missingTransitionTargetLeavesNoRows() {
        TaskDefinitionSaveCommand cmd = withSteps(
                legal(),
                List.of(new TaskStepCommand("only", "only", 1, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("only", "missing", null, 0)));
        assertThatThrownBy(() -> service.saveAggregate(cmd))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.DAG_CYCLE);
        assertThat(definitions.liveCount()).isZero();
        assertThat(children.steps).isEmpty();
    }

    @Test
    void duplicateStepCodeLeavesNoRows() {
        TaskDefinitionSaveCommand cmd = withSteps(
                legal(),
                List.of(
                        new TaskStepCommand("dup", "a", 1, "CLICK", null, null),
                        new TaskStepCommand("dup", "b", 2, "CLICK", null, null)),
                List.of());
        assertThatThrownBy(() -> service.saveAggregate(cmd))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_CODE_DUPLICATE);
        assertThat(definitions.liveCount()).isZero();
        assertThat(children.steps).isEmpty();
    }

    @Test
    void getCopyDeleteAndPage() {
        var saved = service.saveAggregate(legal());
        var view = service.get(saved.id());
        assertThat(view.code()).isEqualTo("daily_check");
        assertThat(view.steps()).hasSize(2);
        assertThat(view.pendingRevision()).isFalse();
        var copied = service.copy(saved.id(), new com.mkt.task.command.TaskCopyCommand("daily_copy", "副本"));
        assertThat(copied.code()).isEqualTo("daily_copy");
        assertThat(definitions.liveCount()).isEqualTo(2);
        service.delete(saved.id());
        assertThat(definitions.getById(saved.id()).deletedFlag()).isTrue();
        var page = service.page(new com.mkt.task.query.TaskDefinitionQuery(
                null, null, "DRAFT", null, com.mkt.kernel.PageQuery.of(1, 20)));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void saveWithGrayFilterAndActions() {
        var cmd = new TaskDefinitionSaveCommand(
                null,
                "gray_task",
                "灰度",
                "desc",
                "daily",
                "https://img",
                "热门",
                null,
                null,
                1,
                "NONE",
                null,
                null,
                null,
                null,
                new com.mkt.task.command.TaskGrayCommand("RATIO", 50, null, null, null),
                new com.mkt.task.command.TaskFilterCommand("province() = 'GD'", List.of(), List.of()),
                List.of(new TaskStepCommand("go", "go", 1, "CLICK", null, null)),
                List.of(),
                List.of(new com.mkt.task.command.TaskActionCommand(
                        "TASK", null, "WEB", "ROUTE", java.util.Map.of("route", "home"), "去完成")));
        var saved = service.saveAggregate(cmd);
        var view = service.get(saved.id());
        assertThat(view.gray().type()).isEqualTo("RATIO");
        assertThat(view.filter().expr()).contains("province");
        assertThat(view.actions()).hasSize(1);
        assertThat(view.iconUrl()).isEqualTo("https://img");
        var updated = service.saveAggregate(new TaskDefinitionSaveCommand(
                saved.id(),
                "gray_task",
                "灰度2",
                "desc",
                "daily",
                "https://img",
                "热门",
                null,
                null,
                1,
                "NONE",
                null,
                null,
                null,
                null,
                new com.mkt.task.command.TaskGrayCommand("NONE", null, null, null, null),
                null,
                List.of(
                        new TaskStepCommand("go", "go", 1, "CLICK", null, null),
                        new TaskStepCommand("done", "done", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go", "done", "userLevel() >= 1", 0)),
                List.of(
                        new com.mkt.task.command.TaskActionCommand(
                                "TASK", null, "WEB", "NONE", null, null),
                        new com.mkt.task.command.TaskActionCommand(
                                "STEP",
                                "go",
                                "WEB",
                                "LINK",
                                java.util.Map.of("url", "https://example.com"),
                                "去"))));
        assertThat(updated.id()).isEqualTo(saved.id());
        var after = service.get(saved.id());
        assertThat(after.steps()).hasSize(2);
        assertThat(after.actions()).hasSize(2);
        assertThat(after.transitions()).hasSize(1);
    }

    @Test
    void lowercaseGrayAndActionScopeAreNormalized() {
        var cmd = new TaskDefinitionSaveCommand(
                null,
                "norm_task",
                "规范",
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "none",
                null,
                null,
                null,
                null,
                new com.mkt.task.command.TaskGrayCommand("ratio", 10, null, null, null),
                null,
                List.of(new TaskStepCommand("go", "go", 1, "click", null, null)),
                List.of(),
                List.of(new com.mkt.task.command.TaskActionCommand(
                        "task", null, "web", "route", java.util.Map.of("route", "home"), "去")));
        var saved = service.saveAggregate(cmd);
        var view = service.get(saved.id());
        assertThat(view.cycleType()).isEqualTo("NONE");
        assertThat(view.gray().type()).isEqualTo("RATIO");
        assertThat(view.steps().get(0).type()).isEqualTo("CLICK");
        assertThat(view.actions()).hasSize(1);
        assertThat(view.actions().get(0).scope()).isEqualTo("TASK");
        assertThat(view.actions().get(0).platform()).isEqualTo("WEB");
        assertThat(view.actions().get(0).actionType()).isEqualTo("ROUTE");
    }

    @Test
    void publishedNotDeletable() {
        var saved = service.saveAggregate(legal());
        var entity = definitions.getById(saved.id());
        entity.setStatus("PUBLISHED");
        definitions.update(entity);
        assertThatThrownBy(() -> service.delete(saved.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.PUBLISHED_NOT_DELETABLE);
    }

    @Test
    void backEdgeRejected() {
        TaskDefinitionSaveCommand cmd = withSteps(
                legal(),
                List.of(
                        new TaskStepCommand("a", "a", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "b", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("b", "a", null, 0)));
        assertThatThrownBy(() -> service.saveAggregate(cmd))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.DAG_CYCLE);
        assertThat(definitions.liveCount()).isZero();
    }

    private static TaskDefinitionSaveCommand legal() {
        return withSteps(
                new TaskDefinitionSaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        null,
                        "daily",
                        null,
                        null,
                        null,
                        null,
                        0,
                        "NONE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of()),
                List.of(
                        new TaskStepCommand("go_page", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go_page", "click", null, 0)));
    }

    private static TaskDefinitionSaveCommand withSteps(
            TaskDefinitionSaveCommand base, List<TaskStepCommand> steps, List<TaskTransitionCommand> edges) {
        return new TaskDefinitionSaveCommand(
                base.id(),
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
                steps,
                edges,
                base.actions());
    }
}
