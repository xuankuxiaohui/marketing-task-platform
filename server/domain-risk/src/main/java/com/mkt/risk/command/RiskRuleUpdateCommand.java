package com.mkt.risk.command;

import com.mkt.contract.RiskAction;
import jakarta.validation.constraints.NotNull;

public record RiskRuleUpdateCommand(
        @NotNull Boolean enabled, @NotNull Long threshold, Long windowSeconds, @NotNull RiskAction action) {}
