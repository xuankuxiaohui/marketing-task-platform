package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PrizeParamRules;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.response.PrizeImpactResponse;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import com.mkt.reward.response.StockReplenishResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrizeAppService {

    private final PrizeStore prizes;
    private final StockLogStore stockLogs;
    private final SnapshotPrizeLookup snapshots;
    private final Clock clock;
    private final PrizeValidate validate;

    @Autowired
    public PrizeAppService(
            PrizeStore prizes,
            StockLogStore stockLogs,
            SnapshotPrizeLookup snapshots,
            Clock clock,
            PrizeValidate validate) {
        this.prizes = prizes;
        this.stockLogs = stockLogs;
        this.snapshots = snapshots;
        this.clock = clock;
        this.validate = validate;
    }

    public PrizeAppService(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            StockLogStore stockLogs,
            SnapshotPrizeLookup snapshots,
            Clock clock) {
        this(prizes, stockLogs, snapshots, clock, new PrizeValidate(categories));
    }

    public PrizeResponse get(long id) {
        return toResponse(require(id));
    }

    public PageData<PrizeResponse> page(PrizeQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = prizes.count(query.code(), query.name(), query.categoryCode(), query.status(), query.groupId());
        List<PrizeEntity> rows = prizes.list(
                query.code(),
                query.name(),
                query.categoryCode(),
                query.status(),
                query.groupId(),
                page.offset(),
                page.pageSize());
        return new PageData<>(total, rows.stream().map(PrizeAppService::toResponse).toList());
    }

    @Transactional
    public PrizeResponse create(PrizeSaveCommand command) {
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
        applyWritable(entity, normalized, category, true);
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
        return toResponse(entity);
    }

    @Transactional
    public PrizeResponse update(long id, PrizeSaveCommand command) {
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
        applyWritable(existing, normalized, category, !frozen);
        if (!frozen) {
            existing.setTotalStock(normalized.totalStock());
        }
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        if (!frozen) {
            prizes.resetDraftStock(id);
            existing.setRemainingStock(normalized.totalStock());
        }
        return toResponse(existing);
    }

    @Transactional
    public void delete(long id) {
        PrizeEntity existing = require(id);
        if (!PrizeStatuses.DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿态可删除");
        }
        if (snapshots.referencedByAnySnapshot(id)) {
            throw new BusinessException(RewardErrorCodes.PRIZE_REFERENCED_BY_SNAPSHOT);
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
    }

    @Transactional
    public PrizeImpactResponse disable(long id, PrizeConfirmCommand command) {
        PrizeEntity existing = require(id);
        if (!PrizeStatuses.ENABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅启用态可停用");
        }
        int affected = snapshots.affectedPublishedTaskCount(id);
        int inFlight = snapshots.inFlightInstanceCount(id);
        if (!Boolean.TRUE.equals(command.confirm())) {
            return new PrizeImpactResponse(false, affected, inFlight);
        }
        existing.setStatus(PrizeStatuses.DISABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        return new PrizeImpactResponse(true, affected, inFlight);
    }

    @Transactional
    public PrizeImpactResponse enable(long id, PrizeConfirmCommand command) {
        PrizeEntity existing = require(id);
        if (PrizeStatuses.ENABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "奖品已启用");
        }
        if (PrizeStatuses.DRAFT.equals(existing.getStatus())) {
            validate.requireEnabledCategory(existing.getCategoryCode());
            existing.setStatus(PrizeStatuses.ENABLED);
            existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            prizes.update(existing);
            return new PrizeImpactResponse(true, 0, 0);
        }
        if (!PrizeStatuses.DISABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态流转非法");
        }
        validate.requireEnabledCategory(existing.getCategoryCode());
        int affected = snapshots.affectedPublishedTaskCount(id);
        int inFlight = snapshots.inFlightInstanceCount(id);
        if (!Boolean.TRUE.equals(command.confirm())) {
            return new PrizeImpactResponse(false, affected, inFlight);
        }
        existing.setStatus(PrizeStatuses.ENABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        return new PrizeImpactResponse(true, affected, inFlight);
    }

    @Transactional
    public StockReplenishResponse replenish(long id, StockReplenishCommand command) {
        PrizeEntity existing = require(id);
        if (command == null || command.amount() == null || command.amount() <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "回补数量必须为正整数");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "回补原因必填");
        }
        int before = existing.getRemainingStock();
        int affected = prizes.replenish(id, command.amount());
        if (affected != 1) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        PrizeEntity after = require(id);
        appendLog(
                id,
                StockChangeTypes.REPLENISH,
                command.amount(),
                before,
                after.getRemainingStock(),
                null,
                command.reason().trim(),
                RewardOperator.optionalUserId());
        return new StockReplenishResponse(after.getRemainingStock());
    }

    public PageData<StockLogView> stockLogs(long id, PageQuery page) {
        require(id);
        long total = stockLogs.countByPrize(id);
        List<StockLogEntity> rows = stockLogs.listByPrize(id, page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(PrizeAppService::toLogView).toList());
    }

    private PrizeEntity require(long id) {
        PrizeEntity existing = prizes.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static void applyWritable(
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

    private void appendLog(
            long prizeId,
            String changeType,
            int amount,
            int before,
            int after,
            String bizSource,
            String bizId,
            Long operatorId) {
        StockLogEntity log = new StockLogEntity();
        log.setPrizeId(prizeId);
        log.setChangeType(changeType);
        log.setAmount(amount);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setBizSource(bizSource);
        log.setBizId(bizId);
        log.setOperatorId(operatorId);
        log.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        stockLogs.insert(log);
    }

    private static PrizeResponse toResponse(PrizeEntity entity) {
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

    private static StockLogView toLogView(StockLogEntity entity) {
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

    private static String emptyToNullJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return RewardJson.strings(values);
    }
}
