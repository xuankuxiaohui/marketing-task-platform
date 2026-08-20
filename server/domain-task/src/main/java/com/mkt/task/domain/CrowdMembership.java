package com.mkt.task.domain;

/** Crowd pack membership by id (gray / filter). Missing or disabled packs are not hits. */
public interface CrowdMembership {

    boolean contains(long crowdId, long userId);
}
