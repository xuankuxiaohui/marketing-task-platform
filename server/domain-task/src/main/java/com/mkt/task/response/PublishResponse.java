package com.mkt.task.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublishResponse(
        boolean requiresConfirm,
        String message,
        Integer inFlightInstanceCount,
        long id,
        String code,
        int version,
        String status) {

    public static final String REVISION_HINT = "新实例将使用新版本，存量实例不受影响";

    public static final String SCHEDULED_REVISION_HINT = "仅发布修订草稿，主状态保持 SCHEDULED，到点由调度固化版本";

    public static PublishResponse preview(long id, String code, int version, String status, int inFlight) {
        String hint = "SCHEDULED".equals(status) ? SCHEDULED_REVISION_HINT : REVISION_HINT;
        return new PublishResponse(true, hint, inFlight, id, code, version, status);
    }

    public static PublishResponse done(long id, String code, int version, String status) {
        return new PublishResponse(false, null, null, id, code, version, status);
    }
}
