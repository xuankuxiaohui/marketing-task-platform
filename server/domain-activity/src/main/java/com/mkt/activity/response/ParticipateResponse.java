package com.mkt.activity.response;

public record ParticipateResponse(long participationId, String result, boolean granted, Long grantRecordId) {}
