package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record PrizeCategorySaveCommand(
        @NotBlank @Size(min = 4, max = 32) String code,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 16) String rewardTarget,
        @NotBlank @Size(max = 16) String fulfillmentMode,
        @NotBlank @Size(max = 16) String costMode,
        @NotNull Boolean reconRequired,
        @NotBlank @Size(max = 16) String reconActionPolicy,
        @Size(max = 32) String adapterCode,
        Map<String, Object> paramSchema) {}
