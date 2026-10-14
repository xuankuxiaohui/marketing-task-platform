package com.mkt.reward.application;

import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import org.springframework.stereotype.Component;

/**
 * View mapping helpers extracted from {@link PrizeAppService} (R6-3).
 * Move-only: toResponse / toLogView semantics unchanged.
 */
@Component
public class PrizeViews {

    PrizeResponse toResponse(PrizeEntity entity) {
        return new PrizeResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getImageUrl(),
                entity.getDescription(),
                entity.getCategoryCode(),
                RewardJson.map(entity.getTypeParams()),
                entity.getRewardTarget(),
                entity.getFulfillmentMode(),
                entity.getUnitCostFen(),
                entity.getTotalStock(),
                entity.getRemainingStock(),
                entity.getDailyClaimLimit(),
                entity.getTotalClaimLimit(),
                RewardJson.strings(entity.getRegionLimit()),
                RewardJson.strings(entity.getLevelLimit()),
                RewardJson.strings(entity.getTagLimit()),
                entity.getClaimMode(),
                entity.getReconActionPolicy(),
                entity.getExpireHours(),
                entity.getGroupId(),
                entity.getStatus(),
                RewardJson.map(entity.getExtConfig()),
                RewardTime.toInstant(entity.getCreatedAt()));
    }

    StockLogView toLogView(StockLogEntity entity) {
        return new StockLogView(
                entity.getId(),
                entity.getPrizeId(),
                entity.getChangeType(),
                entity.getAmount(),
                entity.getBeforeValue(),
                entity.getAfterValue(),
                entity.getBizSource(),
                entity.getBizId(),
                entity.getOperatorId(),
                RewardTime.toInstant(entity.getCreatedAt()));
    }
}
