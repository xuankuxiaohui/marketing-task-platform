package com.mkt.task.application;

/** User+task+cycle lookup key for batch instance reads (F01). */
public record TaskCycleKey(long taskId, String cycleKey) {}
