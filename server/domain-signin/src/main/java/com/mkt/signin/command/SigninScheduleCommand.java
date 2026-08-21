package com.mkt.signin.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SigninScheduleCommand(@NotNull Instant publishAt) {}
