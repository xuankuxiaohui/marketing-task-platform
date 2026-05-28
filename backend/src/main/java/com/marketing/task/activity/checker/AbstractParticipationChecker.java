package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractParticipationChecker implements ParticipationChecker {

    protected abstract RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config);

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        CheckerConfig config = findConfig(activity);
        if (config == null) {
            return RuleCheckResult.pass();
        }
        try {
            return doCheck(activity, context, config);
        } catch (Exception e) {
            log.warn("Checker执行异常: type={}, activityCode={}, error={}", checkerType(), activity.getCode(), e.getMessage());
            return RuleCheckResult.pass();
        }
    }

    private CheckerConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> checkers = (List<Map<String, Object>>) rules.getOrDefault("checkers", Collections.emptyList());
            for (Map<String, Object> c : checkers) {
                if (checkerType().equals(c.get("type"))) {
                    CheckerConfig config = new CheckerConfig();
                    config.setType((String) c.get("type"));
                    config.setParams((Map<String, Object>) c.get("params"));
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
