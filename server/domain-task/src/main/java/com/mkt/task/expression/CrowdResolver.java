package com.mkt.task.expression;

/** Crowd membership for {@code inCrowd} (R11.9). Missing/disabled packs are false + warn. */
public interface CrowdResolver {

    boolean contains(String crowdCode);
}
