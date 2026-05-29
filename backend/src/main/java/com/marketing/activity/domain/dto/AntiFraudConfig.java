package com.marketing.activity.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AntiFraudConfig {
    private String type;
    private Map<String, Object> params;
}
