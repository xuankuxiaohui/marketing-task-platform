package com.mkt.identity.support;

/** R2.3: interceptor-stage 403 still writes audit.log. */
@FunctionalInterface
public interface ForbiddenAuditSink {

    void onForbidden(long userId, String username, String method, String path);
}
