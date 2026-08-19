package com.mkt.identity.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record PortalUserQuery(
        String username,
        String nickname,
        String province,
        String level,
        String tag,
        String status,
        Instant registeredFrom,
        Instant registeredTo,
        PageQuery page) {}
