package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ReconBatchCreateCommand(
        @NotBlank @Size(max = 32) String categoryCode, @NotNull LocalDate billDate) {}
