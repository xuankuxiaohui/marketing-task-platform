package com.mkt.task.expression;

import com.mkt.contract.UserAttributes;
import java.time.Instant;

/** FilterContext from UserAttributes (design §5.10). Missing fields stay null → NULL_ATTR. */
public final class EvalContexts {

    private EvalContexts() {}

    public static EvalContext filter(UserAttributes attrs, Instant now, CrowdResolver crowds) {
        if (attrs == null) {
            return EvalContext.missingAttributes(now, crowds);
        }
        return new EvalContext(
                attrs.province(),
                attrs.userRole(),
                attrs.orgId(),
                attrs.userLevel(),
                attrs.tags(),
                attrs.registeredAt(),
                now,
                null,
                null,
                null,
                crowds);
    }

    public static EvalContext branch(
            UserAttributes attrs,
            Instant now,
            CrowdResolver crowds,
            String stepCode,
            Integer progressCurrent,
            Integer progressTarget) {
        if (attrs == null) {
            return new EvalContext(
                    null, null, null, null, null, null, now, stepCode, progressCurrent, progressTarget, crowds);
        }
        return new EvalContext(
                attrs.province(),
                attrs.userRole(),
                attrs.orgId(),
                attrs.userLevel(),
                attrs.tags(),
                attrs.registeredAt(),
                now,
                stepCode,
                progressCurrent,
                progressTarget,
                crowds);
    }
}
