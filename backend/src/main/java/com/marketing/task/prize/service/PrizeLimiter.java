package com.marketing.task.prize.service;

import com.marketing.task.context.UserContext;
import com.marketing.task.prize.domain.entity.Prize;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PrizeLimiter {
    Optional<String> check(UserContext user, Prize prize, LocalDateTime now);
}
