package com.mkt.admin.simulate;

import jakarta.validation.constraints.NotNull;

public record SimulateFlowCommand(@NotNull Long userId, @NotNull Long taskId) {}
