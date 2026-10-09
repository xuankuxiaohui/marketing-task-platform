package com.mkt.task.response;

/** Task identity for a TASK_STEP grant sourceId (instance step id). */
public record TaskStepSource(long taskId, String taskName) {}
