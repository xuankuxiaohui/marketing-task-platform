package com.mkt.admin.simulate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulateCallbackCommand(
        @NotNull Long userId,
        @NotNull Long instanceId,
        @NotBlank @Size(max = 64) String stepCode,
        @Size(max = 64) String bizNo) {}
