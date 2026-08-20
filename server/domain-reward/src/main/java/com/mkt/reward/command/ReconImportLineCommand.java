package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ReconImportLineCommand(
        @NotBlank @Size(max = 64) String fulfillmentRef, @NotNull Integer amountFen, Instant channelTime) {}
