package com.mkt.reward.application;

import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Field-apply helpers extracted from {@link PrizeAppService} (R6-2).
 * Move-only: applyWritable / emptyToNullJson semantics unchanged.
 */
@Component
public class PrizeApply {

    void applyWritable(
            PrizeEntity entity, PrizeSaveCommand command, PrizeCategoryEntity category, boolean copyCategory) {
        entity.setCode(command.code());
        entity.setName(command.name());
        entity.setImageUrl(command.imageUrl());
        entity.setDescription(command.description());
        entity.setDailyClaimLimit(command.dailyClaimLimit());
        entity.setTotalClaimLimit(command.totalClaimLimit());
        entity.setRegionLimit(emptyToNullJson(command.regionLimit()));
        entity.setLevelLimit(emptyToNullJson(command.levelLimit()));
        entity.setTagLimit(emptyToNullJson(command.tagLimit()));
        entity.setClaimMode(command.claimMode());
        entity.setReconActionPolicy(command.reconActionPolicy());
        entity.setExpireHours(command.expireHours());
        entity.setGroupId(command.groupId());
        entity.setExtConfig(RewardJson.map(command.extConfig()));
        if (copyCategory) {
            entity.setCategoryCode(category.getCode());
            entity.setRewardTarget(category.getRewardTarget());
            entity.setFulfillmentMode(category.getFulfillmentMode());
            entity.setTypeParams(RewardJson.map(command.typeParams()));
            entity.setUnitCostFen(command.unitCostFen());
            entity.setTotalStock(command.totalStock());
        }
    }

    String emptyToNullJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return RewardJson.strings(values);
    }
}
