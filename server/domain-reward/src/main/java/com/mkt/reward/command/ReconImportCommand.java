package com.mkt.reward.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReconImportCommand(@NotEmpty List<@Valid @NotNull ReconImportLineCommand> lines) {}
