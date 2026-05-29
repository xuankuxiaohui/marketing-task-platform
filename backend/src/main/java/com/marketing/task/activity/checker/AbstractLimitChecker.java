package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.marketing.task.activity.domain.dto.LimitConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.service.RateLimiter;
import com.marketing.task.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractLimitChecker implements ParticipationChecker {

    protected abstract String buildKey(Activity activity, ParticipationContext ctx);
    protected abstract RateLimiter getRateLimiter();

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        LimitConfig config = findConfig(activity);
        if (config == null) {
            return RuleCheckResult.pass();
        }
        try {
            String key = buildKey(activity, context);
            boolean allowed = getRateLimiter().tryAcquire(key, config.getMax(), Duration.ofDays(1));
            if (!allowed) {
                return RuleCheckResult.fail("LIMIT_EXCEEDED", "参与次数已达上限", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("LimitChecker执行异常: type={}, activityCode={}, error={}", checkerType(), activity.getCode(), e.getMessage());
            return RuleCheckResult.fail("CHECKER_ERROR", "规则校验异常，请稍后重试", checkerType());
        }
    }

    private LimitConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        Map<String, Object> rules = JsonUtil.jsonToObj(activity.getParticipationRules(), new TypeReference<>() {});
        if (rules == null) return null;
        List<Map<String, Object>> limits = (List<Map<String, Object>>) rules.getOrDefault("limits", Collections.emptyList());
        for (Map<String, Object> l : limits) {
            if (checkerType().equals(l.get("scope"))) {
                LimitConfig config = new LimitConfig();
                config.setScope((String) l.get("scope"));
                config.setMax(((Number) l.get("max")).intValue());
                return config;
            }
        }
        return null;
    }
}
