package com.marketing.task.prize.service.limiters;

import com.marketing.task.context.UserContext;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.service.PrizeLimiter;
import com.marketing.task.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class TagLimiter implements PrizeLimiter {

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getLimitsJson() == null || prize.getLimitsJson().isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> tagRule = parseTagRule(prize.getLimitsJson());
        if (tagRule == null) {
            return Optional.empty();
        }

        String type = (String) tagRule.get("type");
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) tagRule.get("list");
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        Set<String> userTags = user.getTags();
        if (userTags == null || userTags.isEmpty()) {
            userTags = Collections.emptySet();
        }

        boolean hasAnyTag = list.stream().anyMatch(userTags::contains);

        if ("deny".equals(type) && hasAnyTag) {
            return Optional.of("您的标签被限制参与");
        }
        if ("allow".equals(type) && !hasAnyTag) {
            return Optional.of("您没有参与该奖品所需的标签");
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTagRule(String limitsJson) {
        try {
            Map<String, Object> limits = JsonUtil.jsonToObj(limitsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (limits == null) return null;
            Object tags = limits.get("tags");
            if (tags instanceof Map) {
                return (Map<String, Object>) tags;
            }
        } catch (Exception e) {
            log.warn("Failed to parse tag rule from limits_json: {}", limitsJson, e);
        }
        return null;
    }
}
