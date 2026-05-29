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
@Order(202)
@RequiredArgsConstructor
public class GlobalDailyLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.GLOBAL_DAILY.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "act:gd:%d:%s".formatted(activity.getId(), date);
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
