package com.mkt.portal.attribution;

import com.mkt.activity.application.ActivityPortalAppService;
import com.mkt.activity.convert.ActivityOwnership;
import com.mkt.contract.GrantSource;
import com.mkt.reward.application.PrizeSourceLookup;
import com.mkt.signin.application.SigninPortalAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.response.TaskStepSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PortalPrizeSourceLookup implements PrizeSourceLookup {

    private final TaskPortalAppService tasks;
    private final ActivityPortalAppService activities;
    private final SigninPortalAppService signin;

    public PortalPrizeSourceLookup(
            TaskPortalAppService tasks, ActivityPortalAppService activities, SigninPortalAppService signin) {
        this.tasks = tasks;
        this.activities = activities;
        this.signin = signin;
    }

    @Override
    public SourceRef resolve(String grantSource, String sourceId) {
        if (grantSource == null || sourceId == null || sourceId.isBlank()) {
            return SourceRef.empty();
        }
        if (GrantSource.TASK_STEP.name().equals(grantSource)) {
            return fromTaskStep(sourceId);
        }
        if (GrantSource.ACTIVITY_PARTICIPATION.name().equals(grantSource)) {
            return fromParticipation(sourceId);
        }
        if (GrantSource.SIGNIN_DAY.name().equals(grantSource)) {
            return fromSignin(sourceId);
        }
        return SourceRef.empty();
    }

    private SourceRef fromTaskStep(String sourceId) {
        Long stepId = parseLong(sourceId);
        if (stepId == null) {
            return SourceRef.empty();
        }
        TaskStepSource step = tasks.sourceForStep(stepId);
        if (step == null) {
            return SourceRef.empty();
        }
        List<ActivityOwnership.Ref> refs = activities.ownershipForTask(step.taskId());
        ActivityOwnership.Ref first = ActivityOwnership.first(refs);
        return new SourceRef(
                step.taskId(),
                step.taskName(),
                first == null ? null : first.activityId(),
                ActivityOwnership.joinedNames(refs));
    }

    private SourceRef fromParticipation(String sourceId) {
        Long participationId = parseLong(sourceId);
        if (participationId == null) {
            return SourceRef.empty();
        }
        ActivityOwnership.Ref ref = activities.ownershipForParticipation(participationId);
        if (ref == null) {
            return SourceRef.empty();
        }
        return new SourceRef(null, null, ref.activityId(), ref.activityName());
    }

    private SourceRef fromSignin(String sourceId) {
        int colon = sourceId.indexOf(':');
        String raw = colon < 0 ? sourceId : sourceId.substring(0, colon);
        Long activityId = parseLong(raw);
        if (activityId == null) {
            return SourceRef.empty();
        }
        return new SourceRef(null, null, activityId, signin.activityName(activityId));
    }

    private static Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
