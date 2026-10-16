package com.mkt.task.application;

import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskInstanceEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Instance expiry extracted from {@link TaskInstanceAppService} (R24-4).
 * Move-only: expireDue / EXPIRE_BATCH semantics unchanged.
 */
@Component
public class InstanceExpire {

    static final int EXPIRE_BATCH = 100;

    private final TaskInstanceStore instances;
    private final InstanceExpireOps expireOps;
    private final Clock clock;

    @Autowired
    public InstanceExpire(TaskInstanceStore instances, InstanceExpireOps expireOps, Clock clock) {
        this.instances = instances;
        this.expireOps = expireOps;
        this.clock = clock;
    }

    int expireDue() {
        Instant now = clock.instant();
        LocalDateTime nowUtc = TaskTime.toUtc(now);
        int total = 0;
        List<TaskInstanceEntity> due;
        do {
            due = instances.listDueToExpire(nowUtc, EXPIRE_BATCH);
            for (TaskInstanceEntity row : due) {
                boolean flipped = expireOps.runInTx(() -> expireOps.expireOne(row.getId(), now));
                if (flipped) {
                    total++;
                }
            }
        } while (due.size() == EXPIRE_BATCH);
        return total;
    }
}
