package com.mkt.reward.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PointsAdjustCommand(
        @NotNull Long userId, @NotNull Long amount, @Size(max = 255) String reason) {}
