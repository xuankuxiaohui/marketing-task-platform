package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record SessionKickCommand(@NotBlank String accountType, @NotBlank String account, String tokenLast4) {}
