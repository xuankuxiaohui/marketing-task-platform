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
public class DeviceFingerprintChecker implements ParticipationChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return AntiFraudType.DEVICE_FINGERPRINT.name();
    }

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        AntiFraudConfig config = findConfig(activity);
        if (config == null || context.getDeviceId() == null) {
            return RuleCheckResult.pass();
        }
        try {
            int maxPerDevice = ((Number) config.getParams().get("maxPerDevice")).intValue();
            String key = "act:dev:%d:%s".formatted(activity.getId(), context.getDeviceId());
            boolean allowed = rateLimiter.tryAcquire(key, maxPerDevice, Duration.ofHours(24));
            if (!allowed) {
                return RuleCheckResult.fail("DEVICE_RATE_EXCEEDED", "设备参与次数已达上限", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("DeviceFingerprintChecker执行异常: activityCode={}", activity.getCode(), e);
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
