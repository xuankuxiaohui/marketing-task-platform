package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.LimitScope;
import com.marketing.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@Order(200)
@RequiredArgsConstructor
public class UserDailyLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.USER_DAILY.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "act:uld:%d:%s:%s".formatted(activity.getId(), ctx.getUserId(), date);
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
