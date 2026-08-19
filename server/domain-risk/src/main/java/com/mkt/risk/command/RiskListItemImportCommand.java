package com.mkt.risk.command;

import com.mkt.contract.RiskListType;
import com.mkt.risk.domain.RiskDimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RiskListItemImportCommand(
        @NotNull RiskDimension dimension,
        @NotNull RiskListType listType,
        @NotBlank String content,
        @NotBlank @Size(max = 255) String reason) {
}
