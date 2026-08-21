package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconActionCommand(
        @NotBlank @Size(max = 24) String action,
        @Size(max = 255) String reason,
        Long userId,
        Long prizeId) {}
