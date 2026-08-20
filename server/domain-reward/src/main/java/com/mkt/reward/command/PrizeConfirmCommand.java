package com.mkt.reward.command;

import jakarta.validation.constraints.NotNull;

public record PrizeConfirmCommand(@NotNull Boolean confirm) {}
