package com.mkt.signin.response;

public record GrantFeedbackView(int day, long prizeId, String status, boolean hitIdempotent) {}
