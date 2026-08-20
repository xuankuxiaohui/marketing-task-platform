package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrowdSaveCommand(
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 16) String status) {}
