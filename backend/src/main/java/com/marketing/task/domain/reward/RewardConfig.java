package com.marketing.task.domain.reward;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class RewardConfig {
    private String type;
    private Integer amount;
    private String name;
    private Map<String, Object> extra = new HashMap<>();

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extra == null) extra = new HashMap<>();
        if (!"type".equals(key) && !"amount".equals(key) && !"name".equals(key)) {
            extra.put(key, value);
        }
    }
}
