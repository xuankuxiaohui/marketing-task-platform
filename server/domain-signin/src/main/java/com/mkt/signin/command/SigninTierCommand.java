package com.mkt.signin.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SigninTierCommand(@NotNull @Min(1) Integer day, @NotNull Long prizeId) {}
