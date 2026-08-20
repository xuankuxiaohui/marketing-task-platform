package com.mkt.reward.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FulfillmentCallbackCommand(
        @NotBlank @Size(max = 64) String fulfillmentRef,
        @NotBlank @Size(max = 16) String result,
        @Size(max = 64) String failReason) {}
