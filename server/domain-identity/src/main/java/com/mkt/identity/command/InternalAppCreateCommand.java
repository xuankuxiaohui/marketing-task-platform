package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalAppCreateCommand(@NotBlank @Size(max = 64) String appName) {}
