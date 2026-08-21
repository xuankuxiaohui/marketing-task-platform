package com.mkt.activity.application;

import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.HitRuleCountView;
import java.util.List;

public interface ActivityParticipationStore {

    int insert(ActParticipationEntity entity);

    int countPass(long activityId, Long userId, String periodKey);

    long countByQuery(Long activityId, Long userId, String result, String periodKey);

    List<ActParticipationEntity> listByQuery(
            Long activityId, Long userId, String result, String periodKey, long offset, int limit);

    long countAll(long activityId);

    long countPassByActivity(long activityId);

    List<HitRuleCountView> countRejectByRule(long activityId);
}
