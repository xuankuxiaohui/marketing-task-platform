package com.marketing.task.prize.service.limiters;

import com.marketing.task.context.UserContext;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.service.PrizeLimiter;
import com.marketing.task.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class LevelLimiter implements PrizeLimiter {

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getLimitsJson() == null || prize.getLimitsJson().isBlank()) {
            return Optional.empty();
        }
        if (user.getLevel() == null) {
            return Optional.empty();
        }

        try {
            Map<String, Object> limits = JsonUtil.jsonToObj(prize.getLimitsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (limits == null) return Optional.empty();

            Object minLevel = limits.get("min_level");
            if (minLevel instanceof Number && user.getLevel() < ((Number) minLevel).intValue()) {
                return Optional.of("您的等级不足，需要≥" + ((Number) minLevel).intValue() + "级");
            }

            Object maxLevel = limits.get("max_level");
            if (maxLevel instanceof Number && user.getLevel() > ((Number) maxLevel).intValue()) {
                return Optional.of("您的等级超出限制，需要≤" + ((Number) maxLevel).intValue() + "级");
            }
        } catch (Exception e) {
            log.warn("Failed to parse level limit from limits_json: {}", prize.getLimitsJson(), e);
        }
        return Optional.empty();
    }
}
