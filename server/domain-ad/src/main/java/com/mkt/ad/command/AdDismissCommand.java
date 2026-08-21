package com.mkt.ad.command;

import jakarta.validation.constraints.NotBlank;

public record AdDismissCommand(@NotBlank String positionCode) {}
