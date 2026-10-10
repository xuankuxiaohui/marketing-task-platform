package com.mkt.contract;

/** Cross-domain read-only crowd membership (DEC-003 / F05). Owned by domain-task. */
public interface CrowdPort {

    /** Whether {@code userId} is in an ENABLED crowd pack by id. Missing/disabled = false. */
    boolean memberOf(long crowdId, long userId);

    /** Whether {@code userId} is in an ENABLED crowd pack by code. Missing/disabled = false. */
    boolean memberOfCode(String crowdCode, long userId);
}
