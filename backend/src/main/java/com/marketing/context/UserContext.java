package com.marketing.context;

import com.marketing.task.domain.enums.Platform;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class UserContext {
    private String userId;
    private String province;
    private String role;
    private Set<String> tags;
    private String orgId;
    private Integer level;
    private Platform platform;
}
