package com.mkt.task.application;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.kernel.BusinessException;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Claim admission checks extracted from {@link TaskClaimAppService} (R11-1).
 * Move-only: checkRisk / checkMutex / checkDailyLimit semantics unchanged.
 */
@Component
public class ClaimChecks {

    private final TaskMutexGroupStore mutexGroups;
    private final TaskInstanceStore instances;
    private final RiskCheckPort risk;
    private final TaskSettings settings;

    @Autowired
    public ClaimChecks(
            TaskMutexGroupStore mutexGroups,
            TaskInstanceStore instances,
            ObjectProvider<RiskCheckPort> risk,
            TaskSettings settings) {
        this(mutexGroups, instances, risk.getIfAvailable(), settings);
    }

    public ClaimChecks(
            TaskMutexGroupStore mutexGroups, TaskInstanceStore instances, RiskCheckPort risk, TaskSettings settings) {
        this.mutexGroups = mutexGroups;
        this.instances = instances;
        this.risk = risk;
        this.settings = settings;
    }

    void checkRisk(long userId, String ip, String deviceId, boolean simulated) {
        if (risk == null) {
            return;
        }
        String resolvedIp = ip == null || ip.isBlank() || "0.0.0.0".equals(ip) ? null : ip;
        RiskVerdict verdict =
                risk.check(RiskScene.CLAIM, new RiskSubject(userId, resolvedIp, deviceId, null, simulated));
        if (verdict.action() == RiskAction.REJECT || verdict.action() == RiskAction.SILENT_REJECT) {
            throw new BusinessException(TaskErrorCodes.RISK_BLOCKED_GENERIC);
        }
    }

    void checkMutex(SnapshotContent snapshot, long userId, String cycleKey) {
        String mutexCode = snapshot.mutexGroupCode();
        if (mutexCode == null || mutexCode.isBlank()) {
            return;
        }
        TaskMutexGroupEntity group = mutexGroups.getByCode(mutexCode);
        if (group == null) {
            return;
        }
        String matchCycle = group.crossCycleFlag() ? null : cycleKey;
        if (instances.existsMutexInProgress(userId, mutexCode, matchCycle)) {
            throw new BusinessException(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
        }
    }

    void checkDailyLimit(long userId, Instant now) {
        ZonedDateTime zoned = now.atZone(CycleKeyResolver.ZONE);
        LocalDateTime from = TaskTime.toUtc(zoned.toLocalDate().atStartOfDay(CycleKeyResolver.ZONE).toInstant());
        LocalDateTime to = TaskTime.toUtc(
                zoned.toLocalDate().plusDays(1).atStartOfDay(CycleKeyResolver.ZONE).toInstant());
        int today = instances.countToday(userId, from, to);
        if (today >= settings.dailyLimitPerUser()) {
            throw new BusinessException(TaskErrorCodes.CLAIM_DAILY_LIMIT);
        }
    }
}
