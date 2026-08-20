package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TaskActionCommand(
        @NotBlank @Size(max = 16) String scope,
        @Size(max = 64) String stepCode,
        @NotBlank @Size(max = 16) String platform,
        @NotBlank @Size(max = 16) String actionType,
        Map<String, Object> params,
        @Size(max = 16) String buttonText) {}
