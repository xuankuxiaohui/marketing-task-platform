package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.domain.PrizeParamRules;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Create and update entries extracted from {@link PrizeAppService} (R33-1).
 * Move-only: create / update semantics unchanged. {@code require} is a save-side copy;
 * the facade keeps the shared require used by get / delete / enable / disable / replenish / stockLogs.
 */
@Component
public class PrizeSave {

    private final PrizeStore prizes;
    private final Clock clock;
    private final PrizeValidate validate;
    private final PrizeApply apply;
    private final PrizeViews views;

    @Autowired
    public PrizeSave(PrizeStore prizes, Clock clock, PrizeValidate validate, PrizeApply apply, PrizeViews views) {
        this.prizes = prizes;
        this.clock = clock;
        this.validate = validate;
        this.apply = apply;
        this.views = views;
    }

    PrizeResponse create(PrizeSaveCommand command) {
        PrizeSaveCommand normalized = validate.requireValid(command);
        PrizeCategoryEntity category = validate.requireEnabledCategory(normalized.categoryCode());
        PrizeParamRules.requireValid(
                category.getCode(),
                category.getCostMode(),
                category.getRewardTarget(),
                category.getAdapterCode(),
                normalized.unitCostFen(),
                normalized.typeParams());
        if (prizes.getByCode(normalized.code()) != null) {
            throw new BusinessException(RewardErrorCodes.PRIZE_DUPLICATE_CODE);
        }
        PrizeEntity entity = new PrizeEntity();
        apply.applyWritable(entity, normalized, category, true);
        entity.setStatus(PrizeStatuses.DRAFT);
        entity.setDeleted(0);
        entity.setRemainingStock(normalized.totalStock());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            prizes.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(RewardErrorCodes.PRIZE_DUPLICATE_CODE, ex);
        }
        return views.toResponse(entity);
    }

    PrizeResponse update(long id, PrizeSaveCommand command) {
        PrizeEntity existing = require(id);
        PrizeSaveCommand normalized = validate.requireValid(command);
        if (!existing.getCode().equals(normalized.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        boolean frozen = PrizeStatuses.everEnabled(existing.getStatus());
        PrizeCategoryEntity category;
        if (frozen) {
            if (!existing.getCategoryCode().equals(normalized.categoryCode())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "启用后不可改分类");
            }
            category = validate.requireCategory(existing.getCategoryCode());
            validate.assertCostFieldsUnchanged(existing, normalized);
        } else {
            category = validate.requireEnabledCategory(normalized.categoryCode());
        }
        PrizeParamRules.requireValid(
                category.getCode(),
                category.getCostMode(),
                category.getRewardTarget(),
                category.getAdapterCode(),
                frozen ? existing.getUnitCostFen() : normalized.unitCostFen(),
                frozen ? RewardJson.map(existing.getTypeParams()) : normalized.typeParams());
        apply.applyWritable(existing, normalized, category, !frozen);
        if (!frozen) {
            existing.setTotalStock(normalized.totalStock());
        }
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        if (!frozen) {
            prizes.resetDraftStock(id);
            existing.setRemainingStock(normalized.totalStock());
        }
        return views.toResponse(existing);
    }

    private PrizeEntity require(long id) {
        PrizeEntity existing = prizes.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
