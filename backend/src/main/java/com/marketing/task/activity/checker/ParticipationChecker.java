package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;

public interface ParticipationChecker {
    RuleCheckResult check(Activity activity, ParticipationContext context);
    String checkerType();
}
