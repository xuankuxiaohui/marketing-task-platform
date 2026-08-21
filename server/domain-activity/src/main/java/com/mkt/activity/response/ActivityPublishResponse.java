package com.mkt.activity.response;

public record ActivityPublishResponse(
        boolean requiresConfirm, String message, long id, String code, int version, String status) {

    public static final String REVISION_HINT = "新参与将使用新版本，已产生的记录按参与时配置结算";

    public static ActivityPublishResponse preview(long id, String code, int version, String status) {
        return new ActivityPublishResponse(true, REVISION_HINT, id, code, version, status);
    }

    public static ActivityPublishResponse done(long id, String code, int version, String status) {
        return new ActivityPublishResponse(false, null, id, code, version, status);
    }
}
