package com.mkt.identity.command;

import jakarta.validation.constraints.Size;
import java.util.List;

public record PortalUserProfileCommand(
        @Size(max = 32) String province,
        @Size(max = 32) String userLevel,
        @Size(max = 32) String userRole,
        List<String> tags,
        @Size(max = 64) String orgId) {}
