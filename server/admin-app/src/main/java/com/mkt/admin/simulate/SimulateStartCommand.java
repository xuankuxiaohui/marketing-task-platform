package com.mkt.admin.simulate;

import jakarta.validation.constraints.NotNull;

public record SimulateStartCommand(@NotNull Long userId, @NotNull Long taskId) {}
