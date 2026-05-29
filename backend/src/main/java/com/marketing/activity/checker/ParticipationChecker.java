package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;

public interface ParticipationChecker {
    RuleCheckResult check(Activity activity, ParticipationContext context);
    String checkerType();
}
