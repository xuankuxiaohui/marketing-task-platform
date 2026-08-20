package com.mkt.task.response;

public record TaskProgressResponse(
        long instanceId, String stepCode, int progressCurrent, Integer progressTarget, String stepStatus) {}
