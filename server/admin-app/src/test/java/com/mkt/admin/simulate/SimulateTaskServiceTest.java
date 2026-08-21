package com.mkt.admin.simulate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.application.StockLogStore;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.application.TaskVersionSnapshotStore;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.response.TaskStartResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimulateTaskServiceTest {

    private TaskPortalAppService portal;
    private TaskClaimAppService claims;
    private TaskStepAppService steps;
    private TaskInstanceStore instances;
    private SimulateGrantLookup grants;
    private PrizeStore prizes;
    private StockLogStore stockLogs;
    private PointsAppService points;
    private SimulateTaskService service;

    @BeforeEach
    void setUp() {
        portal = mock(TaskPortalAppService.class);
        claims = mock(TaskClaimAppService.class);
        steps = mock(TaskStepAppService.class);
        instances = mock(TaskInstanceStore.class);
        grants = mock(SimulateGrantLookup.class);
        prizes = mock(PrizeStore.class);
        stockLogs = mock(StockLogStore.class);
        points = mock(PointsAppService.class);
        service = new SimulateTaskService(
                portal,
                claims,
                steps,
                instances,
                mock(TaskVersionSnapshotStore.class),
                grants,
                prizes,
                stockLogs,
                points,
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void listAndDetailRequireUserId() {
        assertThatThrownBy(() -> service.list(null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        when(portal.list(8L, null, 1, 20)).thenReturn(new PageData<>(0, List.of()));
        assertThat(service.list(8L, null, 1, 20).total()).isZero();
        when(portal.detail(3L, 8L, "SIMULATOR"))
                .thenReturn(new TaskDetailResponse("NONE", null, null, List.of(), null, List.of(), null));
        TaskDetailResponse detail = service.detail(8L, 3L);
        assertThat(detail.status()).isEqualTo("NONE");
    }

    @Test
    void startClickCallbackProgressUseSimulatedInstance() {
        when(claims.start(eq(3L), eq(8L), anyString(), eq("simulate"), eq("SIMULATOR"), eq(true)))
                .thenReturn(new TaskStartResponse(11L, "IN_PROGRESS", null));
        assertThat(service.start(new SimulateStartCommand(8L, 3L), "10.0.0.1").instanceId()).isEqualTo(11L);

        TaskInstanceEntity simulated = instance(11L, 8L, 1);
        when(instances.getById(11L)).thenReturn(simulated);
        when(steps.click(eq(11L), eq("c1"), eq(8L), anyString(), eq("simulate"), eq("SIMULATOR")))
                .thenReturn(new TaskClickResponse(11L, "COMPLETED", "IN_PROGRESS", null, List.of()));
        assertThat(service.click(new SimulateClickCommand(8L, 11L, "c1"), "10.0.0.1").stepStatus())
                .isEqualTo("COMPLETED");

        when(steps.callback(any(InternalCallbackCommand.class)))
                .thenReturn(new TaskCallbackResponse(11L, "cb", "COMPLETED", "IN_PROGRESS"));
        assertThat(service.callback(new SimulateCallbackCommand(8L, 11L, "cb", null)).stepCode()).isEqualTo("cb");

        when(steps.progress(any(InternalProgressCommand.class)))
                .thenReturn(new TaskProgressResponse(11L, "pg", 3, 3, "COMPLETED"));
        assertThat(service.progress(new SimulateProgressCommand(8L, 11L, "pg", 3, "r1")).progressCurrent())
                .isEqualTo(3);
    }

    @Test
    void clickRejectsRealInstance() {
        when(instances.getById(11L)).thenReturn(instance(11L, 8L, 0));
        assertThatThrownBy(() -> service.click(new SimulateClickCommand(8L, 11L, "c1"), "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void flowAdvancesClickThenStopsWhenComplete() {
        when(claims.start(eq(3L), eq(8L), anyString(), eq("simulate"), eq("SIMULATOR"), eq(true)))
                .thenReturn(new TaskStartResponse(
                        11L, "IN_PROGRESS", new CurrentStepView("c1", "click", "CLICK", 0, null, null)));
        TaskInstanceEntity simulated = instance(11L, 8L, 1);
        TaskInstanceStepEntity click = step(21L, 11L, "c1", StepTypes.CLICK, StepStatuses.ACTIVE);
        when(instances.getById(11L)).thenReturn(simulated);
        when(instances.listSteps(11L))
                .thenReturn(List.of(click))
                .thenReturn(completed(click))
                .thenReturn(completed(click));
        when(steps.click(eq(11L), eq("c1"), eq(8L), anyString(), eq("simulate"), eq("SIMULATOR")))
                .thenReturn(new TaskClickResponse(11L, "COMPLETED", "COMPLETED", null, List.of()));
        when(grants.listByStepSourceIds(List.of("21"))).thenReturn(List.of());
        SimulateFlowResponse flow = service.flow(new SimulateFlowCommand(8L, 3L), "10.0.0.1");
        assertThat(flow.instanceId()).isEqualTo(11L);
        assertThat(flow.steps()).hasSize(1);
        assertThat(flow.steps().getFirst().action()).isEqualTo("click");
    }

    @Test
    void reverseRestocksPointsAndMarksSendingWithoutChannel() {
        TaskInstanceEntity simulated = instance(11L, 8L, 1);
        TaskInstanceStepEntity reward = step(21L, 11L, "r1", StepTypes.REWARD, StepStatuses.COMPLETED);
        when(instances.getById(11L)).thenReturn(simulated);
        when(instances.listSteps(11L)).thenReturn(List.of(reward));
        SimulateGrantRow arrived = new SimulateGrantRow(501L, 9L, 8L, "TASK_STEP", "21", "ARRIVED", "POINTS");
        SimulateGrantRow sending = new SimulateGrantRow(502L, 9L, 8L, "TASK_STEP", "21", "SENDING", "PHYSICAL");
        when(grants.listByStepSourceIds(List.of("21"))).thenReturn(List.of(arrived, sending));
        when(grants.reverseLogExists(anyLong())).thenReturn(false);
        when(grants.reversalExists(anyString())).thenReturn(false);
        when(grants.earnAmount(8L, "TASK_STEP", "21")).thenReturn(40L);
        PrizeEntity prize = new PrizeEntity();
        prize.setId(9L);
        prize.setRemainingStock(4);
        when(prizes.getById(9L)).thenReturn(prize);
        when(prizes.restoreOne(9L)).thenAnswer(invocation -> {
            prize.setRemainingStock(prize.getRemainingStock() + 1);
            return 1;
        });

        SimulateReverseResponse result = service.reverse(new SimulateReverseCommand(11L));
        assertThat(result.channelRevoked()).isFalse();
        assertThat(result.stockRestored()).isEqualTo(2);
        assertThat(result.sendingMarked()).isEqualTo(1);
        assertThat(result.pointsReversed()).isEqualTo(1);
        verify(points).reverse(8L, 40, "TASK_STEP", "sim-rev:501", "simulate reverse", true);
        ArgumentCaptor<StockLogEntity> logs = ArgumentCaptor.forClass(StockLogEntity.class);
        verify(stockLogs, org.mockito.Mockito.times(2)).insert(logs.capture());
        assertThat(logs.getAllValues())
                .allMatch(row -> StockChangeTypes.SIMULATE_REVERSE.equals(row.getChangeType()));
        verify(points, never()).reverse(anyLong(), anyInt(), anyString(), eq("sim-rev:502"), anyString(), anyBoolean());
    }

    @Test
    void reverseRejectsNonSimulatedInstance() {
        when(instances.getById(11L)).thenReturn(instance(11L, 8L, 0));
        assertThatThrownBy(() -> service.reverse(new SimulateReverseCommand(11L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    private static TaskInstanceEntity instance(long id, long userId, int simulated) {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setId(id);
        row.setUserId(userId);
        row.setSimulated(simulated);
        row.setStatus("IN_PROGRESS");
        return row;
    }

    private static TaskInstanceStepEntity step(long id, long instanceId, String code, String type, String status) {
        TaskInstanceStepEntity row = new TaskInstanceStepEntity();
        row.setId(id);
        row.setInstanceId(instanceId);
        row.setStepCode(code);
        row.setType(type);
        row.setStatus(status);
        row.setProgressCurrent(0);
        return row;
    }

    private static List<TaskInstanceStepEntity> completed(TaskInstanceStepEntity row) {
        TaskInstanceStepEntity copy = step(row.getId(), row.getInstanceId(), row.getStepCode(), row.getType(), StepStatuses.COMPLETED);
        return new ArrayList<>(List.of(copy));
    }
}
