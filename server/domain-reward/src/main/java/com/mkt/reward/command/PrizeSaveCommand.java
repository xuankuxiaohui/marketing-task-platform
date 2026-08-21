package com.mkt.reward.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record PrizeSaveCommand(
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String imageUrl,
        @Size(max = 1024) String description,
        @NotBlank @Size(max = 32) String categoryCode,
        Map<String, Object> typeParams,
        @Min(1) Integer unitCostFen,
        @NotNull @Min(1) Integer totalStock,
        @NotNull @Min(0) Integer dailyClaimLimit,
        @NotNull @Min(0) Integer totalClaimLimit,
        List<String> regionLimit,
        List<String> levelLimit,
        List<String> tagLimit,
        @NotBlank @Size(max = 16) String claimMode,
        @Size(max = 16) String reconActionPolicy,
        @Min(1) Integer expireHours,
        Long groupId,
        Map<String, Object> extConfig) {}
