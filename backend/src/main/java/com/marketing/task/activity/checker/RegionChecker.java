package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.CheckerType;
import org.springframework.stereotype.Component;

@Component
public class RegionChecker extends AbstractParticipationChecker {

    @Override
    public String checkerType() {
        return CheckerType.REGION.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        // Region check requires client to pass region info in context
        // For now, always pass - region info needs to be added to ParticipationContext
        return RuleCheckResult.pass();
    }
}
