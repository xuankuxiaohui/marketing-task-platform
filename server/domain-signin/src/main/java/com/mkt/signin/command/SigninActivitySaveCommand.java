package com.mkt.signin.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record SigninActivitySaveCommand(
        Long id,
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Valid List<SigninTierCommand> tiers) {}
