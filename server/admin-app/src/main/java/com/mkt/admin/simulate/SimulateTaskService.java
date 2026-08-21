package com.mkt.admin.simulate;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.application.StockLogStore;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.GrantRecordStatuses;
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
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.domain.Platforms;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-process simulator (design §3.11.4 / R24). Always simulated=true. */
public final class SimulateTaskService {

    static final String DEVICE = "simulate";
    static final int FLOW_GUARD = 64;

    private final TaskPortalAppService portal;
    private final TaskClaimAppService claims;
    private final TaskStepAppService steps;
    private final TaskInstanceStore instances;
    private final TaskVersionSnapshotStore snapshots;
    private final SimulateGrantLookup grants;
    private final PrizeStore prizes;
    private final StockLogStore stockLogs;
    private final PointsAppService points;
    private final Clock clock;

    public SimulateTaskService(
            TaskPortalAppService portal,
            TaskClaimAppService claims,
            TaskStepAppService steps,
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            SimulateGrantLookup grants,
            PrizeStore prizes,
            StockLogStore stockLogs,
            PointsAppService points,
            Clock clock) {
        this.portal = portal;
        this.claims = claims;
        this.steps = steps;
        this.instances = instances;
        this.snapshots = snapshots;
        this.grants = grants;
        this.prizes = prizes;
        this.stockLogs = stockLogs;
        this.points = points;
        this.clock = clock;
    }

    public PageData<TaskCardView> list(Long userId, String category, Integer page, Integer pageSize) {
        return portal.list(requireUser(userId), category, page, pageSize);
    }

    public TaskDetailResponse detail(Long userId, Long taskId) {
        if (taskId == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return portal.detail(taskId, requireUser(userId), Platforms.SIMULATOR);
    }

    public TaskStartResponse start(SimulateStartCommand command, String ip) {
        requireCommand(command == null || command.userId() == null || command.taskId() == null);
        return claims.start(
                command.taskId(), command.userId(), resolveIp(ip), DEVICE, Platforms.SIMULATOR, true);
    }

    public TaskClickResponse click(SimulateClickCommand command, String ip) {
        requireCommand(command == null || command.userId() == null || command.instanceId() == null);
        requireSimulatedInstance(command.instanceId(), command.userId());
        return steps.click(
                command.instanceId(),
                command.stepCode(),
                command.userId(),
                resolveIp(ip),
                DEVICE,
                Platforms.SIMULATOR);
    }

    public TaskCallbackResponse callback(SimulateCallbackCommand command) {
        requireCommand(command == null || command.userId() == null || command.instanceId() == null);
        requireSimulatedInstance(command.instanceId(), command.userId());
        String bizNo = command.bizNo() == null || command.bizNo().isBlank()
                ? "sim-" + command.instanceId() + "-" + command.stepCode()
                : command.bizNo().trim();
        return steps.callback(new InternalCallbackCommand(
                command.instanceId(), command.userId(), null, null, command.stepCode(), bizNo));
    }

    public TaskProgressResponse progress(SimulateProgressCommand command) {
        requireCommand(command == null || command.userId() == null || command.instanceId() == null);
        requireSimulatedInstance(command.instanceId(), command.userId());
        return steps.progress(new InternalProgressCommand(
                command.instanceId(),
                command.userId(),
                null,
                null,
                command.stepCode(),
                command.value(),
                command.reportId()));
    }

    public SimulateFlowResponse flow(SimulateFlowCommand command, String ip) {
        requireCommand(command == null || command.userId() == null || command.taskId() == null);
        TaskStartResponse started =
                claims.start(command.taskId(), command.userId(), resolveIp(ip), DEVICE, Platforms.SIMULATOR, true);
        long instanceId = started.instanceId();
        List<SimulateFlowStepView> trail = new ArrayList<>();
        for (int i = 0; i < FLOW_GUARD; i++) {
            TaskInstanceEntity instance = instances.getById(instanceId);
            TaskInstanceStepEntity active = activeStep(instanceId);
            if (instance == null || active == null) {
                break;
            }
            String type = active.getType();
            String code = active.getStepCode();
            if (StepTypes.CLICK.equals(type)) {
                TaskClickResponse result =
                        steps.click(instanceId, code, command.userId(), resolveIp(ip), DEVICE, Platforms.SIMULATOR);
                trail.add(new SimulateFlowStepView(code, type, "click", result.stepStatus()));
            } else if (StepTypes.CALLBACK.equals(type)) {
                TaskCallbackResponse result = steps.callback(new InternalCallbackCommand(
                        instanceId, command.userId(), null, null, code, "sim-" + instanceId + "-" + code));
                trail.add(new SimulateFlowStepView(code, type, "callback", result.stepStatus()));
            } else if (StepTypes.PROGRESS.equals(type)) {
                int target = progressTarget(instance, code);
                int current = active.getProgressCurrent() == null ? 0 : active.getProgressCurrent();
                int delta = Math.max(1, target - current);
                TaskProgressResponse result = steps.progress(new InternalProgressCommand(
                        instanceId,
                        command.userId(),
                        null,
                        null,
                        code,
                        delta,
                        "sim-" + instanceId + "-" + code + "-" + UUID.randomUUID()));
                trail.add(new SimulateFlowStepView(code, type, "progress", result.stepStatus()));
            } else {
                trail.add(new SimulateFlowStepView(code, type, "blocked", active.getStatus()));
                break;
            }
        }
        TaskInstanceEntity fresh = instances.getById(instanceId);
        String status = fresh == null ? started.instanceStatus() : fresh.getStatus();
        return new SimulateFlowResponse(instanceId, status, List.copyOf(trail), grantIds(instanceId));
    }

    public SimulateReverseResponse reverse(SimulateReverseCommand command) {
        if (command == null || command.instanceId() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskInstanceEntity instance = instances.getById(command.instanceId());
        if (instance == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (instance.getSimulated() == null || instance.getSimulated() != 1) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        List<String> sourceIds = new ArrayList<>();
        List<TaskInstanceStepEntity> rows = instances.listSteps(instance.getId());
        if (rows != null) {
            for (TaskInstanceStepEntity step : rows) {
                if (step.getId() != null) {
                    sourceIds.add(String.valueOf(step.getId()));
                }
            }
        }
        return reverseGrants(instance.getId(), grants.listByStepSourceIds(sourceIds));
    }

    SimulateReverseResponse reverseGrants(long instanceId, List<SimulateGrantRow> rows) {
        int pointsReversed = 0;
        int stockRestored = 0;
        int sendingMarked = 0;
        if (rows != null) {
            for (SimulateGrantRow row : rows) {
                if (!grants.reverseLogExists(row.id()) && restoreStock(row)) {
                    stockRestored++;
                }
                if (GrantRecordStatuses.FULFILL_SENDING.equals(row.fulfillmentStatus())) {
                    sendingMarked++;
                }
                if (GrantRecordStatuses.FULFILL_ARRIVED.equals(row.fulfillmentStatus())
                        && BuiltinCategories.POINTS.equals(row.categoryCode())) {
                    pointsReversed += reversePoints(row);
                }
            }
        }
        return new SimulateReverseResponse(instanceId, pointsReversed, stockRestored, sendingMarked, false);
    }

    private boolean restoreStock(SimulateGrantRow row) {
        if (prizes == null) {
            return false;
        }
        PrizeEntity prize = prizes.getById(row.prizeId());
        if (prize == null) {
            return false;
        }
        int before = prize.getRemainingStock() == null ? 0 : prize.getRemainingStock();
        int affected = prizes.restoreOne(row.prizeId());
        if (affected != 1) {
            return false;
        }
        PrizeEntity after = prizes.getById(row.prizeId());
        int remaining = after == null || after.getRemainingStock() == null ? before + 1 : after.getRemainingStock();
        if (stockLogs != null) {
            StockLogEntity log = new StockLogEntity();
            log.setPrizeId(row.prizeId());
            log.setChangeType(StockChangeTypes.SIMULATE_REVERSE);
            log.setAmount(1);
            log.setBeforeValue(before);
            log.setAfterValue(remaining);
            log.setBizSource(row.grantSource());
            log.setBizId(String.valueOf(row.id()));
            log.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            stockLogs.insert(log);
        }
        return true;
    }

    private int reversePoints(SimulateGrantRow row) {
        if (points == null) {
            return 0;
        }
        String bizId = "sim-rev:" + row.id();
        if (grants.reversalExists(bizId)) {
            return 0;
        }
        Long amount = grants.earnAmount(row.userId(), row.grantSource(), row.sourceId());
        if (amount == null || amount <= 0) {
            return 0;
        }
        int delta = amount.intValue();
        points.reverse(row.userId(), delta, row.grantSource(), bizId, "simulate reverse", true);
        return 1;
    }

    private List<Long> grantIds(long instanceId) {
        List<String> sourceIds = new ArrayList<>();
        List<TaskInstanceStepEntity> rows = instances.listSteps(instanceId);
        if (rows != null) {
            for (TaskInstanceStepEntity step : rows) {
                if (step.getId() != null) {
                    sourceIds.add(String.valueOf(step.getId()));
                }
            }
        }
        List<Long> ids = new ArrayList<>();
        for (SimulateGrantRow row : grants.listByStepSourceIds(sourceIds)) {
            ids.add(row.id());
        }
        return List.copyOf(ids);
    }

    private TaskInstanceStepEntity activeStep(long instanceId) {
        List<TaskInstanceStepEntity> rows = instances.listSteps(instanceId);
        if (rows == null) {
            return null;
        }
        for (TaskInstanceStepEntity step : rows) {
            if (StepStatuses.ACTIVE.equals(step.getStatus())) {
                return step;
            }
        }
        return null;
    }

    private int progressTarget(TaskInstanceEntity instance, String stepCode) {
        if (instance == null || snapshots == null) {
            return 1;
        }
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            return 1;
        }
        SnapshotContent snapshot = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        TaskStepCommand def = SnapshotViews.step(snapshot, stepCode);
        if (def == null || def.progressTarget() == null || def.progressTarget() < 1) {
            return 1;
        }
        return def.progressTarget();
    }

    private TaskInstanceEntity requireSimulatedInstance(long instanceId, long userId) {
        TaskInstanceEntity instance = instances.getById(instanceId);
        if (instance == null || instance.getUserId() == null || instance.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (instance.getSimulated() == null || instance.getSimulated() != 1) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return instance;
    }

    private static long requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return userId;
    }

    private static void requireCommand(boolean invalid) {
        if (invalid) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
    }

    private static String resolveIp(String ip) {
        return ip == null || ip.isBlank() ? "127.0.0.1" : ip;
    }
}
