package com.mkt.identity.command;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleAssignPermissionsCommand(@NotNull List<Long> permissionIds) {}
