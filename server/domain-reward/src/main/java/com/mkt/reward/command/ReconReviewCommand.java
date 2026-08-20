package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconReviewCommand(
        @NotBlank @Size(max = 16) String decision, @NotBlank @Size(max = 255) String remark) {}
