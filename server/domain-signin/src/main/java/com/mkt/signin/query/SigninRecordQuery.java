package com.mkt.signin.query;

import com.mkt.kernel.PageQuery;
import java.time.LocalDate;

public record SigninRecordQuery(Long activityId, Long userId, LocalDate from, LocalDate to, PageQuery page) {}
