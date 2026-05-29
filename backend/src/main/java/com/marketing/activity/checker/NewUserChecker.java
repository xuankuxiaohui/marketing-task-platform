package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.CheckerConfig;
import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.CheckerType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(100)
public class NewUserChecker extends AbstractParticipationChecker {

    @Override
    public String checkerType() {
        return CheckerType.NEW_USER.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        Map<String, Object> params = config.getParams();
        int maxDays = params.containsKey("days") ? ((Number) params.get("days")).intValue() : 7;
        if (context.getUserDays() != null && context.getUserDays() > maxDays) {
            return RuleCheckResult.fail("NOT_NEW_USER", "仅限新用户参与", checkerType());
        }
        return RuleCheckResult.pass();
    }
}
