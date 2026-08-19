package com.mkt.risk.command;

import com.mkt.contract.RiskListType;
import com.mkt.risk.domain.RiskDimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RiskListItemCreateCommand(
        @NotNull RiskDimension dimension,
        @NotNull RiskListType listType,
        @NotBlank @Size(max = 64) String listValue,
        @NotBlank @Size(max = 255) String reason,
        Instant expireAt,
        Boolean denyLogin,
        @Size(max = 255) String remark) {
}
