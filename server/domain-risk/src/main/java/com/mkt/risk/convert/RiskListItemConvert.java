package com.mkt.risk.convert;

import com.mkt.contract.RiskListType;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.response.RiskListItemResponse;
import com.mkt.risk.support.RiskListImportParser;

public final class RiskListItemConvert {

    private RiskListItemConvert() {
    }

    public static RiskListItemResponse toResponse(RiskListItemEntity entity) {
        return new RiskListItemResponse(
                entity.getId(),
                entity.getDimension(),
                entity.getListType(),
                entity.getListValue(),
                entity.getReason(),
                entity.denyLoginFlag(),
                RiskTime.toInstant(entity.getEffectiveAt()),
                RiskTime.toInstant(entity.getExpireAt()),
                entity.getOperatorId(),
                entity.getRemark(),
                RiskTime.toInstant(entity.getCreatedAt()));
    }

    public static ListEntry toEntry(RiskListItemEntity entity) {
        RiskDimension dimension = RiskDimension.valueOf(entity.getDimension());
        String value = RiskListImportParser.normalizeOrNull(dimension, entity.getListValue());
        return new ListEntry(
                dimension,
                RiskListType.valueOf(entity.getListType()),
                value == null ? entity.getListValue() : value,
                RiskTime.toInstant(entity.getExpireAt()),
                entity.denyLoginFlag());
    }
}
