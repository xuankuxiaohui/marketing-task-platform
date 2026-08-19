package com.mkt.risk.application;

import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.mapper.RiskHitLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRiskHitLogStore implements RiskHitLogStore {

    private final RiskHitLogMapper mapper;

    public MybatisRiskHitLogStore(RiskHitLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RiskHitLogEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(RiskHitLogEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public long countByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to) {
        return mapper.selectCountByQuery(ruleCode, hitType, dimensionValue, userId, actionResult, from, to);
    }

    @Override
    public List<RiskHitLogEntity> listByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return mapper.selectListByQuery(
                ruleCode, hitType, dimensionValue, userId, actionResult, from, to, offset, limit);
    }
}
