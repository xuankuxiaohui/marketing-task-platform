package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.LimitScope;
import com.marketing.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(201)
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
