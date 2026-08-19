package com.mkt.risk.command;

import com.mkt.risk.domain.RiskHandleAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RiskCaseHandleCommand(
        Long hitLogId,
        Long userId,
        @NotNull RiskHandleAction action,
        @NotNull Boolean toWhitelist,
        @NotBlank @Size(max = 255) String reason,
        Instant expireAt) {
}
