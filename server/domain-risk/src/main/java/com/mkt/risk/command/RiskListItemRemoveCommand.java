package com.mkt.risk.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RiskListItemRemoveCommand(@NotBlank @Size(max = 255) String reason) {
}
