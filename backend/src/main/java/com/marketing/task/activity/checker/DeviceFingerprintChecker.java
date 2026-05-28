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
