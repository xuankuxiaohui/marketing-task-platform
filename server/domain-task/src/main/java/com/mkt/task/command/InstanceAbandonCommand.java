package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstanceAbandonCommand(@NotBlank @Size(max = 512) String reason) {}
