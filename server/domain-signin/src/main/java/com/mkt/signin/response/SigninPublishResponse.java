package com.mkt.signin.response;

public record SigninPublishResponse(
        boolean requiresConfirm, String message, long id, String code, int version, String status) {

    public static final String REVISION_HINT = "新签到将使用新版本，已产生的记录按签到时快照结算";

    public static SigninPublishResponse preview(long id, String code, int version, String status) {
        return new SigninPublishResponse(true, REVISION_HINT, id, code, version, status);
    }

    public static SigninPublishResponse done(long id, String code, int version, String status) {
        return new SigninPublishResponse(false, null, id, code, version, status);
    }
}
