package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.DiffEntry;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Version list / diff helpers extracted from {@link TaskPublishAppService} (R1-4).
 * Move-only: versions mapping, requireSnapshot, index*, asObject, diffByKey, sameJson unchanged.
 */
@Component
public class TaskPublishVersionDiff {

    private final TaskVersionSnapshotStore snapshots;

    public TaskPublishVersionDiff(TaskVersionSnapshotStore snapshots) {
        this.snapshots = snapshots;
    }

    List<TaskVersionView> listVersions(long taskId) {
        return snapshots.listByTaskId(taskId).stream()
                .map(row -> new TaskVersionView(
                        row.getVersion(), TaskTime.toInstant(row.getPublishedAt()), row.getPublishedBy()))
                .toList();
    }

    VersionDiffResponse diff(long taskId, int left, int right) {
        SnapshotContent leftContent = requireSnapshot(taskId, left);
        SnapshotContent rightContent = requireSnapshot(taskId, right);
        return new VersionDiffResponse(
                diffByKey(indexSteps(leftContent.steps()), indexSteps(rightContent.steps())),
                diffByKey(indexEdges(leftContent.transitions()), indexEdges(rightContent.transitions())),
                asObject(diffByKey(indexFilter(leftContent.filter()), indexFilter(rightContent.filter()))),
                asObject(diffByKey(indexGray(leftContent.gray()), indexGray(rightContent.gray()))),
                diffByKey(indexActions(leftContent.actions()), indexActions(rightContent.actions())));
    }

    private SnapshotContent requireSnapshot(long taskId, int version) {
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, version);
        if (snap == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND, "版本快照不存在");
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private static Map<String, Object> indexSteps(List<TaskStepCommand> steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (steps == null) {
            return map;
        }
        for (TaskStepCommand step : steps) {
            map.put(step.code(), step);
        }
        return map;
    }

    private static Map<String, Object> indexEdges(List<TaskTransitionCommand> edges) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (edges == null) {
            return map;
        }
        for (TaskTransitionCommand edge : edges) {
            map.put(edge.fromStepCode() + "->" + edge.toStepCode(), edge);
        }
        return map;
    }

    private static Map<String, Object> indexFilter(TaskFilterCommand filter) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (filter == null) {
            return map;
        }
        map.put("expr", filter.expr());
        map.put("allowCrowdIds", filter.allowCrowdIds());
        map.put("excludeCrowdIds", filter.excludeCrowdIds());
        return map;
    }

    private static Map<String, Object> indexGray(TaskGrayCommand gray) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (gray == null) {
            return map;
        }
        map.put("type", gray.type());
        map.put("ratio", gray.ratio());
        map.put("abGroup", gray.abGroup());
        map.put("crowdId", gray.crowdId());
        map.put("excludeCrowdId", gray.excludeCrowdId());
        return map;
    }

    private static Map<String, Object> indexActions(List<TaskActionCommand> actions) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (actions == null) {
            return map;
        }
        for (TaskActionCommand action : actions) {
            String key = action.scope() + "|" + action.platform()
                    + (action.stepCode() == null ? "" : "|" + action.stepCode());
            map.put(key, action);
        }
        return map;
    }

    private static Map<String, DiffEntry> asObject(List<DiffEntry> entries) {
        Map<String, DiffEntry> map = new LinkedHashMap<>();
        for (DiffEntry entry : entries) {
            map.put(entry.key(), entry);
        }
        return map;
    }

    private static List<DiffEntry> diffByKey(Map<String, Object> left, Map<String, Object> right) {
        List<DiffEntry> out = new ArrayList<>();
        for (String key : left.keySet()) {
            if (!right.containsKey(key)) {
                out.add(new DiffEntry("REMOVE", key, left.get(key), null));
            } else if (!sameJson(left.get(key), right.get(key))) {
                out.add(new DiffEntry("CHANGE", key, left.get(key), right.get(key)));
            }
        }
        for (String key : right.keySet()) {
            if (!left.containsKey(key)) {
                out.add(new DiffEntry("ADD", key, null, right.get(key)));
            }
        }
        return out;
    }

    private static boolean sameJson(Object left, Object right) {
        return Objects.equals(JsonUtil.toJson(left), JsonUtil.toJson(right));
    }
}
