package com.mkt.task.application;

/** Identifies one published snapshot version for batch reads (F01). */
public record TaskVersionKey(long taskId, int version) {}
