package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.CheckerConfig;
import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.CheckerType;
/**
 * Region checker — not yet implemented. Deactivated by removing @Component
 * so Spring does not register it in the checker chain.
 * Re-enable by adding @Component back once region logic is ready.
 */
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
