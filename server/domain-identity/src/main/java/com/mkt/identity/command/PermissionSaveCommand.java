package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionSaveCommand(
        Long parentId,
        @NotBlank @Size(max = 16) String type,
        @Size(max = 128) String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 128) String route,
        @Size(max = 128) String component,
        @Size(max = 64) String icon,
        Integer sort) {}
