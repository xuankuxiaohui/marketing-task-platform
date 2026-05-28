package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CheckerConfig {
    private String type;
    private Map<String, Object> params;
}
