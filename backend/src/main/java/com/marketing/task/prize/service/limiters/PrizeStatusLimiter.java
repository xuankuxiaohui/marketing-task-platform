package com.marketing.task.prize.service.limiters;

import com.marketing.task.context.UserContext;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.service.PrizeLimiter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class PrizeStatusLimiter implements PrizeLimiter {

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getEnabled() == null || !prize.getEnabled()) {
            return Optional.of("奖品已停用");
        }
        if (prize.getStartTime() != null && now.isBefore(prize.getStartTime())) {
            return Optional.of("奖品尚未开放");
        }
        if (prize.getEndTime() != null && now.isAfter(prize.getEndTime())) {
            return Optional.of("奖品已过期");
        }
        return Optional.empty();
    }
}
