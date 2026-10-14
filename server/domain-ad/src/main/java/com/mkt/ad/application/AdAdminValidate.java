package com.mkt.ad.application;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.domain.AdJumpSchemas;
import com.mkt.ad.domain.AdPlatforms;
import com.mkt.ad.domain.AdStatuses;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validation helpers extracted from {@link AdAdminAppService} (R4-1).
 * Move-only: validateMaterial / validatePlacement semantics unchanged.
 */
@Component
public class AdAdminValidate {

    void validateMaterial(AdMaterialSaveCommand command) {
        if (command.weight() == null || command.weight() < 1 || command.weight() > 999) {
            throw new BusinessException(AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        }
        if (command.startTime() == null
                || command.endTime() == null
                || !command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(AdErrorCodes.TIME_WINDOW_INVALID);
        }
        String jump = AdJumpSchemas.normalize(command.jumpType());
        if (!AdJumpSchemas.validType(jump) || !AdJumpSchemas.validParams(jump, command.jumpParams())) {
            throw new BusinessException(AdErrorCodes.MATERIAL_JUMP_INVALID);
        }
        String image = command.imageUrl() == null ? "" : command.imageUrl().trim();
        if (!image.toLowerCase(Locale.ROOT).startsWith("https://") || image.length() > 512) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "图片须为 https 外链");
        }
        String status = AdStatuses.normalize(command.status());
        if (!AdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态无效");
        }
    }

    void validatePlacement(AdPlacementSaveCommand command) {
        if (command.weight() == null || command.weight() < 1 || command.weight() > 999) {
            throw new BusinessException(AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        }
        if (command.startTime() == null
                || command.endTime() == null
                || !command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(AdErrorCodes.TIME_WINDOW_INVALID);
        }
        String gray = AdGrayTypes.normalize(command.grayType());
        if (!AdGrayTypes.valid(gray)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "灰度配置无效");
        }
        if (AdGrayTypes.RATIO.equals(gray)
                && (command.grayRatio() == null || command.grayRatio() < 0 || command.grayRatio() > 100)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "灰度比例无效");
        }
        String status = AdStatuses.normalize(command.status());
        if (!AdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态无效");
        }
        if (command.platforms() != null) {
            for (String platform : command.platforms()) {
                if (platform != null
                        && !platform.isBlank()
                        && !AdPlatforms.valid(platform.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "端定向无效");
                }
            }
        }
    }
}
