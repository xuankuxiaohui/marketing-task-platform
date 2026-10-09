package com.mkt.reward.application;

/**
 * Optional portal composition hook: resolve a grant's task/activity without domain-to-domain imports.
 */
public interface PrizeSourceLookup {

    SourceRef resolve(String grantSource, String sourceId);

    record SourceRef(Long sourceTaskId, String sourceTaskName, Long activityId, String activityName) {
        public static SourceRef empty() {
            return new SourceRef(null, null, null, null);
        }
    }
}
