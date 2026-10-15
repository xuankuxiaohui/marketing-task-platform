package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Due-schedule publish scan extracted from {@link TaskPublishAppService} (R19-1).
 * Move-only: scanDue / publishDue / SCAN_BATCH semantics unchanged.
 */
@Component
public class PublishScanDue {

    private static final Logger log = LoggerFactory.getLogger(PublishScanDue.class);
    private static final int SCAN_BATCH = 100;

    private final TaskDefinitionStore definitions;
    private final TaskPublishChecks publishChecks;
    private final TaskPublishFreeze publishFreeze;
    private final TaskPublishScheduleFailures scheduleFailures;
    private final Clock clock;
    private final ObjectProvider<PlatformTransactionManager> txm;
    private final TransactionTemplate txDirect;

    @Autowired
    public PublishScanDue(
            TaskDefinitionStore definitions,
            TaskPublishChecks publishChecks,
            TaskPublishFreeze publishFreeze,
            TaskPublishScheduleFailures scheduleFailures,
            Clock clock,
            ObjectProvider<PlatformTransactionManager> txm) {
        this.definitions = definitions;
        this.publishChecks = publishChecks;
        this.publishFreeze = publishFreeze;
        this.scheduleFailures = scheduleFailures;
        this.clock = clock;
        this.txm = txm;
        this.txDirect = null;
    }

    PublishScanDue(
            TaskDefinitionStore definitions,
            TaskPublishChecks publishChecks,
            TaskPublishFreeze publishFreeze,
            TaskPublishScheduleFailures scheduleFailures,
            Clock clock,
            TransactionTemplate tx) {
        this.definitions = definitions;
        this.publishChecks = publishChecks;
        this.publishFreeze = publishFreeze;
        this.scheduleFailures = scheduleFailures;
        this.clock = clock;
        this.txm = null;
        this.txDirect = tx;
    }

    int scanDue() {
        LocalDateTime now = nowUtc();
        List<TaskDefinitionEntity> due = definitions.listDueScheduled(now, SCAN_BATCH);
        int published = 0;
        for (TaskDefinitionEntity row : due) {
            try {
                if (runInTx(() -> publishDue(row.getId()))) {
                    scheduleFailures.removeFailureFingerprint(row.getId());
                    published++;
                }
            } catch (BusinessException ex) {
                scheduleFailures.recordScheduleFailure(
                        row, ex.getMessage(), TaskPublishChecks.checkErrorsOf(ex.data()));
            } catch (RuntimeException ex) {
                log.error("sched:publish-scan failed, taskId={}", row.getId(), ex);
                scheduleFailures.recordScheduleFailure(
                        row,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                        List.of());
            }
        }
        return published;
    }

    boolean publishDue(long id) {
        TaskDefinitionEntity entity = definitions.getByIdForUpdate(id);
        if (entity == null || entity.deletedFlag()) {
            return false;
        }
        if (!DefinitionStatuses.SCHEDULED.equals(entity.getStatus())) {
            return false;
        }
        LocalDateTime now = nowUtc();
        if (entity.getSchedulePublishAt() == null || entity.getSchedulePublishAt().isAfter(now)) {
            return false;
        }
        publishChecks.rejectIfInvalid(entity);
        return publishFreeze.freezeToPublished(entity, false);
    }

    private boolean runInTx(BooleanSupplier action) {
        TransactionTemplate template = tx();
        if (template != null) {
            Boolean result = template.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }

    private TransactionTemplate tx() {
        if (txDirect != null) {
            return txDirect;
        }
        if (txm == null) {
            return null;
        }
        PlatformTransactionManager manager = txm.getIfAvailable();
        return manager == null ? null : new TransactionTemplate(manager);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
