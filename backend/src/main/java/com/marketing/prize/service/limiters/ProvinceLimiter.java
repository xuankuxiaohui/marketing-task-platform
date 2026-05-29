package com.marketing.prize.service.limiters;

import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.service.PrizeLimiter;
import com.marketing.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class ProvinceLimiter implements PrizeLimiter {

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getLimitsJson() == null || prize.getLimitsJson().isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> provinceRule = parseProvinceRule(prize.getLimitsJson());
        if (provinceRule == null) {
            return Optional.empty();
        }

        String type = (String) provinceRule.get("type");
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) provinceRule.get("list");
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        String userProvince = user.getProvince();
        boolean inList = userProvince != null && list.contains(userProvince);

        if ("allow".equals(type) && !inList) {
            return Optional.of("您的省份不在可参与范围");
        }
        if ("deny".equals(type) && inList) {
            return Optional.of("您的省份被限制参与");
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseProvinceRule(String limitsJson) {
        try {
            Map<String, Object> limits = JsonUtil.jsonToObj(limitsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (limits == null) return null;
            Object provinces = limits.get("provinces");
            if (provinces instanceof Map) {
                return (Map<String, Object>) provinces;
            }
        } catch (Exception e) {
            log.warn("Failed to parse province rule from limits_json: {}", limitsJson, e);
        }
        return null;
    }
}
