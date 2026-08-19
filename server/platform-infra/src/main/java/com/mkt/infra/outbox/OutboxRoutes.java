package com.mkt.infra.outbox;

import java.util.List;
import java.util.Map;

/**
 * Closed D-05 / §6.4 routing table. Strings are duplicated from contract so infra does not depend
 * on platform-contract (RL-01).
 */
public final class OutboxRoutes {

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

    private static final Map<String, List<ConsumerDirection>> ROUTES = Map.ofEntries(
            Map.entry(AUDIT_LOG, List.of(ConsumerDirection.SYS_AUDIT_LOG)),
            Map.entry(AUTH_REGISTER_SUCCESS, List.of(ConsumerDirection.EVT_EVENT_LOG, ConsumerDirection.RISK_CNT)),
            Map.entry(AUTH_LOGIN_SUCCESS, List.of(ConsumerDirection.EVT_EVENT_LOG, ConsumerDirection.RISK_CNT)),
            Map.entry(TASK_INSTANCE_START, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(TASK_STEP_COMPLETE, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(TASK_INSTANCE_COMPLETE, List.of(ConsumerDirection.EVT_EVENT_LOG, ConsumerDirection.RISK_CNT)),
            Map.entry(TASK_INSTANCE_ABANDON, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(TASK_INSTANCE_EXPIRE, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(RISK_HIT_RECORDED, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(REWARD_GRANT_SUCCESS, List.of(ConsumerDirection.EVT_EVENT_LOG, ConsumerDirection.RISK_CNT)),
            Map.entry(REWARD_GRANT_FAILED, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(REWARD_FULFILL_ARRIVED, List.of(ConsumerDirection.EVT_EVENT_LOG)),
            Map.entry(REWARD_FULFILL_FAILED, List.of(ConsumerDirection.EVT_EVENT_LOG)));

    private OutboxRoutes() {
    }

    public static List<ConsumerDirection> directions(String eventCode) {
        return ROUTES.getOrDefault(eventCode, List.of());
    }

    public static boolean declared(String eventCode) {
        return ROUTES.containsKey(eventCode);
    }

    public static Map<String, List<ConsumerDirection>> all() {
        return ROUTES;
    }
}
