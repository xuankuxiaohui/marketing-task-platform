package com.mkt.activity.convert;

import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.domain.SubmoduleTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Maps activity TASK submodules to a task id (no table join). */
public final class ActivityOwnership {

    public record Ref(long activityId, String activityName) {}

    public record Module(long id, String name, List<ActivitySubmoduleCommand> submodules) {}

    private ActivityOwnership() {}

    public static List<Ref> forTask(long taskId, List<Module> modules) {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }
        List<Ref> out = new ArrayList<>();
        for (Module module : modules) {
            if (module == null || !ownsTask(module, taskId)) {
                continue;
            }
            out.add(new Ref(module.id(), module.name()));
        }
        out.sort(Comparator.comparingLong(Ref::activityId));
        return List.copyOf(out);
    }

    public static Ref first(List<Ref> refs) {
        return refs == null || refs.isEmpty() ? null : refs.get(0);
    }

    public static String joinedNames(List<Ref> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        String joined = refs.stream()
                .map(Ref::activityName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining("、"));
        return joined.isEmpty() ? null : joined;
    }

    private static boolean ownsTask(Module module, long taskId) {
        if (module.submodules() == null) {
            return false;
        }
        for (ActivitySubmoduleCommand sub : module.submodules()) {
            if (sub != null
                    && SubmoduleTypes.TASK.equals(sub.type())
                    && sub.refId() != null
                    && sub.refId() == taskId) {
                return true;
            }
        }
        return false;
    }
}
