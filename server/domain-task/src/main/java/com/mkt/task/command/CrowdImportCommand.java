package com.mkt.task.command;

import jakarta.validation.constraints.NotNull;

public record CrowdImportCommand(@NotNull String content) {}
