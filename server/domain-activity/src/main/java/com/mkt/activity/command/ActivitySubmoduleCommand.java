package com.mkt.activity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ActivitySubmoduleCommand(@NotBlank String type, @NotNull Long refId, Integer sort) {}
