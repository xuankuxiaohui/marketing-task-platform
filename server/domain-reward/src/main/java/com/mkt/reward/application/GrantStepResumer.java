package com.mkt.reward.application;

import com.mkt.contract.GrantSource;

/**
 * Optional hook after a retry/scheduler grant succeeds (design §5.6.2).
 * Admin-app wires this to domain-task step CAS + cascade. Portal first-path is StepEngine itself.
 */
public interface GrantStepResumer {

    void resume(GrantSource source, String sourceId);
}
