package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record PortalNicknameCommand(@NotBlank String nickname) {}
