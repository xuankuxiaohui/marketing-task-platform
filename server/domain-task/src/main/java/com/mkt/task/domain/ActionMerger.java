package com.mkt.task.domain;

import com.mkt.task.command.TaskActionCommand;
import java.util.List;
import java.util.Map;

/**
 * Platform action fallback (design §4.9.2 / R16.2):
 * step+platform → task+platform → step+WEB → task+WEB → NONE placeholder.
 * {@code NONE} is a hit and stops fallback.
 */
public final class ActionMerger {

    public static final String SCOPE_TASK = "TASK";
    public static final String SCOPE_STEP = "STEP";
    public static final String NONE = "NONE";

    private ActionMerger() {}

    public static TaskActionCommand merge(List<TaskActionCommand> actions, String stepCode, String platform) {
        String resolved = Platforms.normalize(platform);
        TaskActionCommand hit = find(actions, SCOPE_STEP, stepCode, resolved);
        if (hit != null) {
            return hit;
        }
        hit = find(actions, SCOPE_TASK, null, resolved);
        if (hit != null) {
            return hit;
        }
        if (!Platforms.WEB.equals(resolved)) {
            hit = find(actions, SCOPE_STEP, stepCode, Platforms.WEB);
            if (hit != null) {
                return hit;
            }
            hit = find(actions, SCOPE_TASK, null, Platforms.WEB);
            if (hit != null) {
                return hit;
            }
        }
        return nonePlaceholder(stepCode, resolved);
    }

    /** NONE placeholder: a hit that stops fallback (R16.2). */
    public static TaskActionCommand nonePlaceholder(String stepCode, String platform) {
        return new TaskActionCommand(SCOPE_TASK, stepCode, Platforms.normalize(platform), NONE, Map.of(), null);
    }

    private static TaskActionCommand find(
            List<TaskActionCommand> actions, String scope, String stepCode, String platform) {
        if (actions == null) {
            return null;
        }
        for (TaskActionCommand action : actions) {
            if (action == null || !scope.equals(action.scope()) || !platform.equals(action.platform())) {
                continue;
            }
            if (SCOPE_STEP.equals(scope) && (stepCode == null || !stepCode.equals(action.stepCode()))) {
                continue;
            }
            return action;
        }
        return null;
    }

    public static Map<String, Object> params(TaskActionCommand action) {
        if (action == null || action.params() == null) {
            return Map.of();
        }
        return action.params();
    }
}
