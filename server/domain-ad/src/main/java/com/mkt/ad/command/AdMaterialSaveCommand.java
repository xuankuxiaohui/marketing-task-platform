package com.mkt.ad.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record AdMaterialSaveCommand(
        Long id,
        @NotBlank @Size(max = 128) String title,
        @Size(max = 256) String subtitle,
        @NotBlank @Size(max = 512) String imageUrl,
        String jumpType,
        Map<String, Object> jumpParams,
        @NotNull Integer weight,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        String status) {}
