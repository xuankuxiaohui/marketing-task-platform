package com.mkt.identity.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record AuditQuery(
        Long operatorId, String module, String action, String result, Instant from, Instant to, PageQuery page) {}
