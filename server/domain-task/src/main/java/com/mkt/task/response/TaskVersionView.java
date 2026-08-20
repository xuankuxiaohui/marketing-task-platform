package com.mkt.task.response;

import java.time.Instant;

public record TaskVersionView(int version, Instant publishedAt, long publishedBy) {}
