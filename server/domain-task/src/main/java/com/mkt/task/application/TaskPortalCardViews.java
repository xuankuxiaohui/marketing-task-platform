package com.mkt.task.application;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.RewardPreviewView;
import com.mkt.task.response.TaskCardView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.stereotype.Component;

/**
 * Card/mine view helpers extracted from {@link TaskPortalAppService} (R1-2).
 * Move-only: PendingCard / considerPending / toCard / toMine / loadSnapshotsByIds semantics unchanged.
 */
@Component
public class TaskPortalCardViews {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;

    public TaskPortalCardViews(TaskVersionSnapshotStore snapshots, TaskInstanceStore instances) {
        this.snapshots = snapshots;
        this.instances = instances;
    }

    /** Sort key only — avoids building TaskCardView/rewardPreview for every published task. */
    record PendingCard(TaskDefinitionEntity definition, SnapshotContent snapshot, String userStatus) {
        int sortWeight() {
            return snapshot.sortWeight();
        }

        long taskId() {
            return definition.getId();
        }
    }

    Map<Long, SnapshotContent> loadSnapshotsByIds(List<Long> ids) {
        Map<Long, SnapshotContent> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (TaskVersionSnapshotEntity snap : snapshots.listByIds(ids)) {
            if (snap == null || snap.getId() == null || snap.getContent() == null) {
                continue;
            }
            SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
            if (content != null) {
                out.put(snap.getId(), content);
            }
        }
        return out;
    }

    /**
     * Keep only the best {@code retain} pending cards (ascending sort key). Peek is the worst of
     * the kept set — replace when the candidate sorts strictly earlier (lower weight / taskId).
     */
    static void considerPending(
            PriorityQueue<PendingCard> best,
            int retain,
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            String userStatus) {
        if (best.size() < retain) {
            best.offer(new PendingCard(definition, snapshot, userStatus));
            return;
        }
        PendingCard worst = best.peek();
        int cmp = Integer.compare(snapshot.sortWeight(), worst.sortWeight());
        if (cmp == 0) {
            cmp = Long.compare(definition.getId(), worst.taskId());
        }
        if (cmp < 0) {
            best.poll();
            best.offer(new PendingCard(definition, snapshot, userStatus));
        }
    }

    static TaskCardView toCard(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            String userStatus,
            Map<Long, RewardPreviewView> rewards) {
        int weight = snapshot.sortWeight();
        RewardPreviewView reward =
                rewards == null
                        ? SnapshotViews.rewardPreview(snapshot)
                        : rewards.getOrDefault(definition.getId(), SnapshotViews.rewardPreview(snapshot));
        return new TaskCardView(
                definition.getId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.category(),
                snapshot.iconUrl(),
                snapshot.badgeText(),
                reward,
                userStatus,
                weight);
    }

    MineTaskView toMine(TaskInstanceEntity row) {
        TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null ? row.getTaskCode() : content.name();
        String icon = content == null ? null : content.iconUrl();
        String category = content == null ? null : content.category();
        String currentName = null;
        if (InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            List<TaskInstanceStepEntity> steps = instances.listSteps(row.getId());
            if (steps != null) {
                for (TaskInstanceStepEntity step : steps) {
                    if (StepStatuses.ACTIVE.equals(step.getStatus())) {
                        TaskStepCommand def = SnapshotViews.step(content, step.getStepCode());
                        currentName = def == null ? step.getStepCode() : def.name();
                        break;
                    }
                }
            }
        }
        return new MineTaskView(
                row.getId(),
                row.getTaskId(),
                name,
                icon,
                category,
                row.getStatus(),
                currentName,
                TaskTime.toInstant(row.getStartedAt()));
    }
}
