package com.mkt.contract.event;

import java.util.Set;

/**
 * Closed Outbox event codes (D-05 / design §6.4). {@link #AUDIT_LOG} is internal and not in
 * appendix D.
 */
public final class EventCodes {

    public static final String AUDIT_LOG = "audit.log";
    public static final String AUTH_REGISTER_SUCCESS = "auth.register.success";
    public static final String AUTH_LOGIN_SUCCESS = "auth.login.success";
    public static final String TASK_INSTANCE_START = "task.instance.start";
    public static final String TASK_STEP_COMPLETE = "task.step.complete";
    public static final String TASK_INSTANCE_COMPLETE = "task.instance.complete";
    public static final String TASK_INSTANCE_ABANDON = "task.instance.abandon";
    public static final String TASK_INSTANCE_EXPIRE = "task.instance.expire";
    public static final String RISK_HIT_RECORDED = "risk.hit.recorded";
    public static final String REWARD_GRANT_SUCCESS = "reward.grant.success";
    public static final String REWARD_GRANT_FAILED = "reward.grant.failed";
    public static final String REWARD_FULFILL_ARRIVED = "reward.fulfill.arrived";
    public static final String REWARD_FULFILL_FAILED = "reward.fulfill.failed";

    public static final Set<String> ALL = Set.of(
            AUDIT_LOG,
            AUTH_REGISTER_SUCCESS,
            AUTH_LOGIN_SUCCESS,
            TASK_INSTANCE_START,
            TASK_STEP_COMPLETE,
            TASK_INSTANCE_COMPLETE,
            TASK_INSTANCE_ABANDON,
            TASK_INSTANCE_EXPIRE,
            RISK_HIT_RECORDED,
            REWARD_GRANT_SUCCESS,
            REWARD_GRANT_FAILED,
            REWARD_FULFILL_ARRIVED,
            REWARD_FULFILL_FAILED);

    private EventCodes() {
    }

    public static boolean isKnown(String eventCode) {
        return ALL.contains(eventCode);
    }

    public static boolean isInternal(String eventCode) {
        return AUDIT_LOG.equals(eventCode);
    }

    public static boolean isAppendixD(String eventCode) {
        return isKnown(eventCode) && !isInternal(eventCode);
    }

    public static void requireKnown(String eventCode) {
        if (!isKnown(eventCode)) {
            throw new IllegalArgumentException("unknown event code: " + eventCode);
        }
    }
}
