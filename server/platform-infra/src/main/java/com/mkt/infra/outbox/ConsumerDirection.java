package com.mkt.infra.outbox;

/** Declared consumption directions in design §6.4. */
public enum ConsumerDirection {
    SYS_AUDIT_LOG,
    EVT_EVENT_LOG,
    RISK_CNT
}
