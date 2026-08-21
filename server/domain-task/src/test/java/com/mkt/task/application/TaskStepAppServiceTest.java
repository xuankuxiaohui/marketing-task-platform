package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryRewardPort;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import com.mkt.task.testsupport.MemoryTaskProgressReportStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class TaskStepAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryTaskInstanceStore instances;
    private MemoryTaskCrowdStore crowds;
    private MemoryTaskProgressReportStore reports;
    private MemoryRewardPort rewards;
    private UserAttributePort users;
    private RiskCheckPort risk;
    private EventPublisher events;
    private TaskClaimAppService claims;
    private TaskStepAppService steps;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        instances = new MemoryTaskInstanceStore();
        crowds = new MemoryTaskCrowdStore();
        reports = new MemoryTaskProgressReportStore();
        rewards = new MemoryRewardPort();
        users = mock(UserAttributePort.class);
        risk = mock(RiskCheckPort.class);
        events = mock(EventPublisher.class);
        TaskSettings settings = new TaskSettings();
        when(users.lockAndGet(9L)).thenReturn(active());
        when(users.attributes(9L)).thenReturn(active());
        when(risk.check(eq(RiskScene.CLAIM), any())).thenReturn(new RiskVerdict(RiskAction.PASS));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        claims = new TaskClaimAppService(
                definitions,
                snapshots,
                instances,
                crowds,
                new MemoryTaskMutexGroupStore(),
                users,
                risk,
                events,
                rewards,
                clock,
                settings);
        steps = new TaskStepAppService(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                risk,
                events,
                rewards,
                clock,
                settings);
    }

    @Test
    void writePathsUseReadCommitted() throws Exception {
        assertThat(TaskStepAppService.class
                        .getMethod(
                                "click",
                                long.class,
                                String.class,
                                long.class,
                                String.class,
                                String.class,
                                String.class)
                        .getAnnotation(Transactional.class)
                        .isolation())
                .isEqualTo(Isolation.READ_COMMITTED);
        assertThat(TaskStepAppService.class
                        .getMethod("callback", InternalCallbackCommand.class)
                        .getAnnotation(Transactional.class)
                        .isolation())
                .isEqualTo(Isolation.READ_COMMITTED);
        assertThat(TaskStepAppService.class
                        .getMethod("progress", InternalProgressCommand.class)
                        .getAnnotation(Transactional.class)
                        .isolation())
                .isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void clickAdvancesAndReturnsNextStep() {
        long taskId = publish(
                "clk",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "B", 2, "CLICK", null, null)),
                List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        TaskClickResponse clicked = steps.click(started.instanceId(), "a", 9L, "203.0.113.1", null, "WEB");
        assertThat(clicked.stepStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(clicked.instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(clicked.nextStep().stepCode()).isEqualTo("b");
    }

    @Test
    void repeatClickIsIdempotent() {
        long taskId = publish("once", List.of(new TaskStepCommand("a", "A", 1, "CLICK", null, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        steps.click(started.instanceId(), "a", 9L, "203.0.113.1", null, "WEB");
        TaskClickResponse again = steps.click(started.instanceId(), "a", 9L, "203.0.113.1", null, "WEB");
        assertThat(again.stepStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(again.instanceStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(again.rewardFeedback()).isEmpty();
    }

    @Test
    void clickWrongUserIsNotFound() {
        long taskId = publish("own", List.of(new TaskStepCommand("a", "A", 1, "CLICK", null, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        assertThatThrownBy(() -> steps.click(started.instanceId(), "a", 8L, "203.0.113.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_NOT_FOUND);
    }

    @Test
    void clickFrozenIs403() {
        long taskId = publish("frz", List.of(new TaskStepCommand("a", "A", 1, "CLICK", null, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        clearInvocations(risk);
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(1L, List.of(RiskListType.BLACK)));
        assertThatThrownBy(() -> steps.click(started.instanceId(), "a", 9L, "203.0.113.1", "dev", "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_FROZEN);
        assertThat(instances.getStep(started.instanceId(), "a").getStatus()).isEqualTo(StepStatuses.ACTIVE);
        verify(risk).userSummary(9L);
        verify(risk, never()).check(any(), any());
    }

    @Test
    void callbackByUserTaskCycleAndIdempotent() {
        long taskId = publish("cbk", List.of(new TaskStepCommand("cb", "CB", 1, "CALLBACK", null, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        TaskCallbackResponse first = steps.callback(
                new InternalCallbackCommand(null, 9L, "cbk", "NONE", "cb", "biz-1"));
        assertThat(first.stepStatus()).isEqualTo(StepStatuses.COMPLETED);
        TaskCallbackResponse second = steps.callback(
                new InternalCallbackCommand(started.instanceId(), null, null, null, "cb", "biz-2"));
        assertThat(second.stepStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(second.instanceStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(instances.getStep(started.instanceId(), "cb").getLastBizNo()).isEqualTo("biz-2");
    }

    @Test
    void callbackFrozenIsAccountRestricted() {
        long taskId = publish("cbf", List.of(new TaskStepCommand("cb", "CB", 1, "CALLBACK", null, null)), List.of());
        claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        clearInvocations(risk);
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(1L, List.of(RiskListType.BLACK)));
        assertThatThrownBy(() -> steps.callback(new InternalCallbackCommand(null, 9L, "cbf", "NONE", "cb", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.ACCOUNT_RESTRICTED);
        verify(risk).userSummary(9L);
        verify(risk, never()).check(any(), any());
    }

    @Test
    void progressDuplicateReportIdIsIdempotent() {
        long taskId = publish("prg", List.of(new TaskStepCommand("p", "P", 1, "PROGRESS", 10, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        TaskProgressResponse first = steps.progress(
                new InternalProgressCommand(started.instanceId(), null, null, null, "p", 2, "rid"));
        TaskProgressResponse dup = steps.progress(
                new InternalProgressCommand(started.instanceId(), null, null, null, "p", 2, "rid"));
        assertThat(first.progressCurrent()).isEqualTo(2);
        assertThat(dup.progressCurrent()).isEqualTo(2);
        assertThat(dup.stepStatus()).isEqualTo(StepStatuses.ACTIVE);
        assertThat(reports.rows).hasSize(1);
    }

    @Test
    void progressMissingReportIdIsParamInvalid() {
        long taskId = publish("prg2", List.of(new TaskStepCommand("p", "P", 1, "PROGRESS", 10, null)), List.of());
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        assertThatThrownBy(() -> steps.progress(
                        new InternalProgressCommand(started.instanceId(), null, null, null, "p", 1, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void clickThenRewardGrantedReturnsFeedback() {
        long taskId = publish(
                "rwd",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("r", "奖", 2, "REWARD", null, 8L)),
                List.of(new TaskTransitionCommand("a", "r", null, 0)));
        TaskStartResponse started = claims.start(taskId, 9L, "203.0.113.1", null, "WEB");
        TaskClickResponse clicked = steps.click(started.instanceId(), "a", 9L, "203.0.113.1", null, "WEB");
        assertThat(clicked.instanceStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(clicked.rewardFeedback()).hasSize(1);
        assertThat(clicked.rewardFeedback().get(0).prizeName()).isEqualTo("奖");
        assertThat(rewards.calls).hasSize(1);
    }

    private long publish(String code, List<TaskStepCommand> stepDefs, List<TaskTransitionCommand> transitions) {
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setStatus("PUBLISHED");
        entity.setVersion(1);
        entity.setCycleType("NONE");
        entity.setGrayType("NONE");
        entity.setSortWeight(0);
        entity.setDeleted(0);
        definitions.insert(entity);
        SnapshotContent content = new SnapshotContent(
                code,
                code,
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
                stepDefs,
                transitions,
                List.of());
        TaskVersionSnapshotEntity snap = new TaskVersionSnapshotEntity();
        snap.setTaskId(entity.getId());
        snap.setVersion(1);
        snap.setContent(JsonUtil.toJson(content));
        snap.setPublishedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        snap.setPublishedBy(1L);
        snapshots.insert(snap);
        return entity.getId();
    }

    private static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
