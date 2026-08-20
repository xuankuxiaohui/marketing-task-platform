package com.mkt.task.response;

import java.time.Instant;

public record InstanceEventView(String code, Instant time, String payloadSummary) {}
