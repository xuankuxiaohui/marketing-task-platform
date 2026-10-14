package com.mkt.ad.application;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.domain.AdDates;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.domain.AdJumpSchemas;
import com.mkt.ad.domain.AdPlatforms;
import com.mkt.ad.domain.AdStatuses;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Field-apply helpers extracted from {@link AdAdminAppService} (R4-2).
 * Move-only: applyMaterial / applyPlacement semantics unchanged.
 */
@Component
public class AdAdminApply {

    void applyMaterial(AdMaterialEntity entity, AdMaterialSaveCommand command) {
        entity.setTitle(command.title());
        entity.setSubtitle(command.subtitle());
        entity.setImageUrl(command.imageUrl().trim());
        entity.setJumpType(AdJumpSchemas.normalize(command.jumpType()));
        entity.setJumpParams(AdFieldCodec.json(command.jumpParams()));
        entity.setWeight(command.weight());
        entity.setStartTime(AdDates.toUtc(command.startTime()));
        entity.setEndTime(AdDates.toUtc(command.endTime()));
        entity.setStatus(AdStatuses.normalize(command.status()));
    }

    void applyPlacement(AdPositionMaterialEntity entity, AdPlacementSaveCommand command, int fallbackWeight) {
        entity.setWeight(command.weight() == null ? fallbackWeight : command.weight());
        entity.setStartTime(AdDates.toUtc(command.startTime()));
        entity.setEndTime(AdDates.toUtc(command.endTime()));
        List<String> platforms =
                command.platforms() == null || command.platforms().isEmpty()
                        ? List.of()
                        : AdPlatforms.normalize(command.platforms());
        entity.setPlatforms(platforms.isEmpty() ? null : AdFieldCodec.json(platforms));
        String gray = AdGrayTypes.normalize(command.grayType());
        entity.setGrayType(gray);
        entity.setGrayRatio(AdGrayTypes.RATIO.equals(gray) ? command.grayRatio() : null);
        entity.setCrowdId(command.crowdId());
        entity.setStatus(AdStatuses.normalize(command.status()));
    }
}
