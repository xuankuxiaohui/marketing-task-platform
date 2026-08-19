package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminUserCreateCommand(
        @NotBlank @Size(min = 4, max = 30) String username,
        @NotBlank @Size(max = 64) String nickname,
        @NotBlank String password,
        List<Long> roleIds) {}
