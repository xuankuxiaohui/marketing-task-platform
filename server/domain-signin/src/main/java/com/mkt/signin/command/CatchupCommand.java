package com.mkt.signin.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatchupCommand(@NotBlank @Size(min = 10, max = 10) String signDate) {}
