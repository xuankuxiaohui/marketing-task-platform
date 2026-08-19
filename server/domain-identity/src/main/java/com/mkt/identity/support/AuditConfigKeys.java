package com.mkt.identity.support;

/** Appendix A keys used by audit retention (design §6.7-9). */
public final class AuditConfigKeys {

    public static final String RETENTION_DAYS = "retention.audit-days";
    public static final int DEFAULT_RETENTION_DAYS = 180;
    public static final int CLEAN_BATCH = 5000;

    private AuditConfigKeys() {}
}
