package com.mkt.risk.convert;

import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.response.RiskHitLogResponse;

public final class RiskHitLogConvert {

    private RiskHitLogConvert() {
    }

    public static RiskHitLogResponse toResponse(RiskHitLogEntity entity) {
        return new RiskHitLogResponse(
                entity.getId(),
                entity.getHitType(),
                entity.getRuleCode(),
                entity.getUserId(),
                entity.getDimensionValue(),
                entity.getContext(),
                entity.getHitValue(),
                entity.getThreshold(),
                entity.getActionResult(),
                entity.getSimulated() != null && entity.getSimulated() == 1,
                RiskTime.toInstant(entity.getOccurredAt()),
                RiskTime.toInstant(entity.getCreatedAt()));
    }
}
