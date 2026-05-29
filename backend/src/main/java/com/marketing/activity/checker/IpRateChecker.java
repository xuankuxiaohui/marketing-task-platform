package com.marketing.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.marketing.activity.domain.dto.AntiFraudConfig;
import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.AntiFraudType;
import com.marketing.activity.service.RateLimiter;
import com.marketing.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(300)
@RequiredArgsConstructor
public class IpRateChecker implements ParticipationChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return AntiFraudType.IP_RATE.name();
    }

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        AntiFraudConfig config = findConfig(activity);
        if (config == null || context.getClientIp() == null) {
            return RuleCheckResult.pass();
        }
        try {
            int maxPerIp = ((Number) config.getParams().get("maxPerIp")).intValue();
            int windowSec = ((Number) config.getParams().get("windowSeconds")).intValue();
            String key = "act:ip:%d:%s".formatted(activity.getId(), context.getClientIp());
            boolean allowed = rateLimiter.tryAcquire(key, maxPerIp, Duration.ofSeconds(windowSec));
            if (!allowed) {
                return RuleCheckResult.fail("IP_RATE_EXCEEDED", "请求过于频繁，请稍后再试", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("IpRateChecker执行异常: activityCode={}", activity.getCode(), e);
            return RuleCheckResult.fail("CHECKER_ERROR", "规则校验异常，请稍后重试", checkerType());
        }
    }

    private AntiFraudConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        Map<String, Object> rules = JsonUtil.jsonToObj(activity.getParticipationRules(), new TypeReference<>() {});
        if (rules == null) return null;
        List<Map<String, Object>> antiFraud = (List<Map<String, Object>>) rules.getOrDefault("antiFraud", Collections.emptyList());
        for (Map<String, Object> af : antiFraud) {
            if (checkerType().equals(af.get("type"))) {
                AntiFraudConfig config = new AntiFraudConfig();
                config.setType((String) af.get("type"));
                config.setParams((Map<String, Object>) af.get("params"));
                return config;
            }
        }
        return null;
    }
}
