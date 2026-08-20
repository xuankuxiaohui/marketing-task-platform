package com.mkt.task.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleCommand(@NotNull Instant publishAt) {}
