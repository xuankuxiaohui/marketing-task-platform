package com.mkt.identity.command;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record ConfigUpdateCommand(
        @Size(max = 64) String configGroup,
        String status,
        String valueType,
        Boolean masked,
        @Size(max = 255) String remark,
        JsonNode value) {}
