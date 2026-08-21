package com.mkt.activity.application;

import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.mapper.ActParticipationMapper;
import com.mkt.activity.response.HitRuleCountView;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisActivityParticipationStore implements ActivityParticipationStore {

    private final ActParticipationMapper mapper;

    public MybatisActivityParticipationStore(ActParticipationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(ActParticipationEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int countPass(long activityId, Long userId, String periodKey) {
        return mapper.countPass(activityId, userId, periodKey);
    }

    @Override
    public long countByQuery(Long activityId, Long userId, String result, String periodKey) {
        return mapper.selectCountByQuery(activityId, userId, result, periodKey);
    }

    @Override
    public List<ActParticipationEntity> listByQuery(
            Long activityId, Long userId, String result, String periodKey, long offset, int limit) {
        return mapper.selectByQuery(activityId, userId, result, periodKey, offset, limit);
    }

    @Override
    public long countAll(long activityId) {
        return mapper.countAll(activityId);
    }

    @Override
    public long countPassByActivity(long activityId) {
        return mapper.countPassByActivity(activityId);
    }

    @Override
    public List<HitRuleCountView> countRejectByRule(long activityId) {
        return mapper.countRejectByRule(activityId);
    }
}
