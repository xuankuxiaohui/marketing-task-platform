package com.marketing.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.marketing.activity.domain.dto.CheckerConfig;
import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.utils.JsonUtil;
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
            return RuleCheckResult.fail("CHECKER_ERROR", "规则校验异常，请稍后重试", checkerType());
        }
    }

    private CheckerConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        Map<String, Object> rules = JsonUtil.jsonToObj(activity.getParticipationRules(), new TypeReference<>() {});
        if (rules == null) return null;
        List<Map<String, Object>> checkers = (List<Map<String, Object>>) rules.getOrDefault("checkers", Collections.emptyList());
        for (Map<String, Object> c : checkers) {
            if (checkerType().equals(c.get("type"))) {
                CheckerConfig config = new CheckerConfig();
                config.setType((String) c.get("type"));
                config.setParams((Map<String, Object>) c.get("params"));
                return config;
            }
        }
        return null;
    }
}
