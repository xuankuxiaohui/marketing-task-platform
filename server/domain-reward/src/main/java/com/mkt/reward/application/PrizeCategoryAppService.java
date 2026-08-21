package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeCategorySaveCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.CategoryCodes;
import com.mkt.reward.domain.CategoryRules;
import com.mkt.reward.domain.CategoryStatuses;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.domain.RewardTargets;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.response.PrizeCategoryResponse;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrizeCategoryAppService {

    private final PrizeCategoryStore store;
    private final PrizeStore prizes;
    private final Clock clock;

    public PrizeCategoryAppService(PrizeCategoryStore store, PrizeStore prizes, Clock clock) {
        this.store = store;
        this.prizes = prizes;
        this.clock = clock;
    }

    public PrizeCategoryResponse get(String code) {
        return toResponse(require(code));
    }

    public PageData<PrizeCategoryResponse> page(PageQuery page) {
        long total = store.countAll();
        List<PrizeCategoryEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(PrizeCategoryAppService::toResponse).toList());
    }

    @Transactional
    public PrizeCategoryResponse create(PrizeCategorySaveCommand command) {
        PrizeCategorySaveCommand normalized = requireValid(command, true);
        if (store.getByCode(normalized.code()) != null) {
            throw new BusinessException(RewardErrorCodes.CATEGORY_DUPLICATE_CODE);
        }
        PrizeCategoryEntity entity = new PrizeCategoryEntity();
        apply(entity, normalized, true);
        entity.setBuiltin(0);
        entity.setStatus(CategoryStatuses.ENABLED);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            store.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(RewardErrorCodes.CATEGORY_DUPLICATE_CODE, ex);
        }
        return toResponse(entity);
    }

    @Transactional
    public PrizeCategoryResponse update(String code, PrizeCategorySaveCommand command) {
        PrizeCategoryEntity existing = require(code);
        PrizeCategorySaveCommand normalized = requireValid(command, false);
        if (!existing.getCode().equals(normalized.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        apply(existing, normalized, false);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        return toResponse(existing);
    }

    @Transactional
    public void delete(String code) {
        PrizeCategoryEntity existing = require(code);
        if (existing.builtinFlag() || BuiltinCategories.isBuiltin(existing.getCode())) {
            throw new BusinessException(RewardErrorCodes.CATEGORY_BUILTIN_PROTECTED);
        }
        if (prizes.countByCategory(existing.getCode()) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "分类仍被奖品引用");
        }
        store.deleteByCode(existing.getCode());
    }

    @Transactional
    public PrizeCategoryResponse disable(String code) {
        PrizeCategoryEntity existing = require(code);
        existing.setStatus(CategoryStatuses.DISABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        return toResponse(existing);
    }

    @Transactional
    public PrizeCategoryResponse enable(String code) {
        PrizeCategoryEntity existing = require(code);
        existing.setStatus(CategoryStatuses.ENABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        return toResponse(existing);
    }

    private PrizeCategoryEntity require(String code) {
        PrizeCategoryEntity existing = store.getByCode(code);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static PrizeCategorySaveCommand requireValid(PrizeCategorySaveCommand command, boolean creating) {
        if (command == null || !CategoryCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "分类编码格式非法");
        }
        String policy = command.reconActionPolicy() == null
                ? ReconPolicies.REVIEW
                : command.reconActionPolicy().trim();
        CategoryRules.requireShape(
                command.code().trim(),
                command.rewardTarget(),
                command.fulfillmentMode(),
                command.costMode(),
                policy);
        if (RewardTargets.THIRD_PARTY.equals(command.rewardTarget())
                && (command.adapterCode() == null || command.adapterCode().isBlank())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "第三方分类必须指定默认适配器");
        }
        if (creating && BuiltinCategories.isBuiltin(command.code().trim())) {
            throw new BusinessException(RewardErrorCodes.CATEGORY_DUPLICATE_CODE);
        }
        return new PrizeCategorySaveCommand(
                command.code().trim(),
                command.name().trim(),
                command.rewardTarget().trim(),
                command.fulfillmentMode().trim(),
                command.costMode().trim(),
                Boolean.TRUE.equals(command.reconRequired()),
                policy,
                blankToNull(command.adapterCode()),
                command.paramSchema());
    }

    private static void apply(PrizeCategoryEntity entity, PrizeCategorySaveCommand command, boolean creating) {
        if (creating) {
            entity.setCode(command.code());
        }
        entity.setName(command.name());
        entity.setRewardTarget(command.rewardTarget());
        entity.setFulfillmentMode(command.fulfillmentMode());
        entity.setCostMode(command.costMode());
        entity.setReconRequired(Boolean.TRUE.equals(command.reconRequired()) ? 1 : 0);
        entity.setReconActionPolicy(command.reconActionPolicy());
        entity.setAdapterCode(command.adapterCode());
        entity.setParamSchema(RewardJson.map(command.paramSchema()));
    }

    private static PrizeCategoryResponse toResponse(PrizeCategoryEntity entity) {
        return new PrizeCategoryResponse(
                entity.getCode(),
                entity.getName(),
                entity.getRewardTarget(),
                entity.getFulfillmentMode(),
                entity.getCostMode(),
                entity.reconRequiredFlag(),
                entity.getReconActionPolicy(),
                entity.getAdapterCode(),
                RewardJson.map(entity.getParamSchema()),
                entity.builtinFlag(),
                entity.getStatus(),
                RewardTime.toInstant(entity.getCreatedAt()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
