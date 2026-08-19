package com.mkt.risk.application;

import com.mkt.risk.entity.RiskHitLogEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface RiskHitLogStore {

    RiskHitLogEntity getById(long id);

    int insert(RiskHitLogEntity entity);

    long countByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to);

    List<RiskHitLogEntity> listByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit);
}
