package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.AntiFraudConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.AntiFraudType;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
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
            return RuleCheckResult.pass();
        }
    }

    private AntiFraudConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> antiFraud = (List<Map<String, Object>>) rules.getOrDefault("antiFraud", Collections.emptyList());
            for (Map<String, Object> af : antiFraud) {
                if (checkerType().equals(af.get("type"))) {
                    AntiFraudConfig config = new AntiFraudConfig();
                    config.setType((String) af.get("type"));
                    config.setParams((Map<String, Object>) af.get("params"));
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
