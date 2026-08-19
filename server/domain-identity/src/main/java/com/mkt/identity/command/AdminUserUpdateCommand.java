package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminUserUpdateCommand(
        @NotBlank @Size(max = 64) String nickname, @NotNull List<Long> roleIds) {}
