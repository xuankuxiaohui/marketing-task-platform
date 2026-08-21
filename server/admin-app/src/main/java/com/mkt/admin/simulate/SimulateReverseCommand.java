package com.mkt.admin.simulate;

import jakarta.validation.constraints.NotNull;

public record SimulateReverseCommand(@NotNull Long instanceId) {}
