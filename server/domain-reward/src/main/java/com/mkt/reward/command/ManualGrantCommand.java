package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ManualGrantCommand(
        @NotNull Long userId,
        @NotNull Long prizeId,
        @NotBlank @Size(max = 255) String reason,
        List<String> bypassRules) {}
