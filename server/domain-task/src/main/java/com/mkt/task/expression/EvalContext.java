package com.mkt.task.expression;

import java.time.Instant;
import java.util.List;

/**
 * FilterContext / BranchContext projection (design §5.10). Null fields are missing attributes.
 */
public record EvalContext(
        String province,
        String userRole,
        String orgId,
        Integer userLevel,
        List<String> tags,
        Instant registeredAt,
        Instant now,
        String stepCode,
        Integer progressCurrent,
        Integer progressTarget,
        CrowdResolver crowds) {

    public EvalContext {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static EvalContext missingAttributes(Instant now, CrowdResolver crowds) {
        return new EvalContext(null, null, null, null, List.of(), null, now, null, null, null, crowds);
    }

    public boolean tagsMissing() {
        return tags == null || tags.isEmpty();
    }
}
