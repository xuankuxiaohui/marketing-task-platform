package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.LimitScope;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTotalLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.USER_TOTAL.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        return "act:ut:%d:%s".formatted(activity.getId(), ctx.getUserId());
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
