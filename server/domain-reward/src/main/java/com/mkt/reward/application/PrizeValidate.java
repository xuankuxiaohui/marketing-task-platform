package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.domain.CategoryStatuses;
import com.mkt.reward.domain.ClaimModes;
import com.mkt.reward.domain.PrizeCodes;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validation helpers extracted from {@link PrizeAppService} (R6-1).
 * Move-only: requireValid / requireCategory / requireEnabledCategory / assertCostFieldsUnchanged
 * semantics unchanged.
 */
@Component
public class PrizeValidate {

    private final PrizeCategoryStore categories;

    @Autowired
    public PrizeValidate(PrizeCategoryStore categories) {
        this.categories = categories;
    }

    PrizeSaveCommand requireValid(PrizeSaveCommand command) {
        if (command == null || !PrizeCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "奖品编码格式非法");
        }
        if (command.totalStock() == null || command.totalStock() <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "总库存必须为正整数");
        }
        if (command.dailyClaimLimit() == null || command.dailyClaimLimit() < 0
                || command.totalClaimLimit() == null
                || command.totalClaimLimit() < 0) {
            throw new BusinessException(RewardErrorCodes.PRIZE_LIMIT_NEGATIVE);
        }
        if (!ClaimModes.valid(command.claimMode())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "领取方式非法");
        }
        String policy = blankToNull(command.reconActionPolicy());
        if (policy != null && !ReconPolicies.valid(policy)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "对账动作政策非法");
        }
        return new PrizeSaveCommand(
                command.code().trim(),
                command.name().trim(),
                blankToNull(command.imageUrl()),
                blankToNull(command.description()),
                command.categoryCode().trim(),
                command.typeParams() == null ? Map.of() : command.typeParams(),
                command.unitCostFen(),
                command.totalStock(),
                command.dailyClaimLimit(),
                command.totalClaimLimit(),
                command.regionLimit(),
                command.levelLimit(),
                command.tagLimit(),
                command.claimMode().trim(),
                policy,
                command.expireHours(),
                command.groupId(),
                command.extConfig());
    }

    PrizeCategoryEntity requireCategory(String code) {
        PrizeCategoryEntity category = categories.getByCode(code);
        if (category == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "奖品分类不存在");
        }
        return category;
    }

    PrizeCategoryEntity requireEnabledCategory(String code) {
        PrizeCategoryEntity category = requireCategory(code);
        if (!CategoryStatuses.ENABLED.equals(category.getStatus())) {
            throw new BusinessException(RewardErrorCodes.PRIZE_CATEGORY_DISABLED);
        }
        return category;
    }

    void assertCostFieldsUnchanged(PrizeEntity existing, PrizeSaveCommand command) {
        Map<String, Object> current = RewardJson.map(existing.getTypeParams());
        Map<String, Object> next = command.typeParams() == null ? Map.of() : command.typeParams();
        if (!Objects.equals(current, next)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "启用后不可改成本算法相关字段");
        }
        if (!Objects.equals(existing.getUnitCostFen(), command.unitCostFen())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "启用后不可改成本算法相关字段");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
