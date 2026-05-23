package com.marketing.task.service.reward;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.marketing.task.domain.reward.RewardConfig;
import com.marketing.task.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RewardConfigParser {

    public RewardConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return new RewardConfig();
        }
        try {
            return JsonUtil.jsonToObjV2(json, RewardConfig.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse reward config JSON: {}", json, e);
            RewardConfig fallback = new RewardConfig();
            fallback.setType("unknown");
            return fallback;
        }
    }
}
