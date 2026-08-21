package com.mkt.signin.response;

import java.util.List;

public record SigninActionResponse(
        boolean alreadySigned,
        String message,
        long recordId,
        String source,
        int consecutiveDays,
        List<GrantFeedbackView> rewards) {}
