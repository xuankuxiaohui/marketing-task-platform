package com.mkt.signin.response;

import java.time.Instant;
import java.time.LocalDate;

public record SigninRecordView(
        long id, long activityId, long userId, LocalDate signDate, String source, Instant createdAt) {}
