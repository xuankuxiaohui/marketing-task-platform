package com.marketing.prize.service;

import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PrizeLimiter {
    Optional<String> check(UserContext user, Prize prize, LocalDateTime now);
}
