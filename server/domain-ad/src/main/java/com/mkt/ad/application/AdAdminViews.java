package com.mkt.ad.application;

import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.domain.AdDates;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.response.AdMaterialView;
import com.mkt.ad.response.AdPlacementView;
import com.mkt.ad.response.AdPositionView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * View mapping helpers extracted from {@link AdAdminAppService} (R4-3).
 * Move-only: toPositionView / toPlacementView / toMaterialView / overlapCount semantics unchanged.
 */
@Component
public class AdAdminViews {

    private final AdMaterialStore materials;
    private final AdPlacementStore placements;

    public AdAdminViews(AdMaterialStore materials, AdPlacementStore placements) {
        this.materials = materials;
        this.placements = placements;
    }

    AdPositionView toPositionView(AdPositionEntity entity) {
        List<AdPositionMaterialEntity> rels = placements.listByPositionId(entity.getId());
        List<Long> ids = rels.stream().map(AdPositionMaterialEntity::getMaterialId).toList();
        Map<Long, AdMaterialEntity> byId = new HashMap<>();
        for (AdMaterialEntity material : materials.listByIds(ids)) {
            byId.put(material.getId(), material);
        }
        List<AdPlacementView> views = new ArrayList<>();
        for (AdPositionMaterialEntity rel : rels) {
            AdMaterialEntity material = byId.get(rel.getMaterialId());
            views.add(toPlacementView(rel, material));
        }
        return new AdPositionView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getForm(),
                AdFieldCodec.strings(entity.getPlatforms()),
                entity.getStatus(),
                views,
                overlapCount(views));
    }

    AdPlacementView toPlacementView(AdPositionMaterialEntity rel, AdMaterialEntity material) {
        return new AdPlacementView(
                rel.getId(),
                rel.getPositionId(),
                rel.getMaterialId(),
                rel.weightOrZero(),
                AdDates.toInstant(rel.getStartTime()),
                AdDates.toInstant(rel.getEndTime()),
                AdFieldCodec.strings(rel.getPlatforms()),
                rel.getGrayType(),
                rel.getGrayRatio(),
                rel.getCrowdId(),
                rel.getStatus(),
                material == null ? null : toMaterialView(material));
    }

    AdMaterialView toMaterialView(AdMaterialEntity entity) {
        return new AdMaterialView(
                entity.getId(),
                entity.getTitle(),
                entity.getSubtitle(),
                entity.getImageUrl(),
                entity.getJumpType(),
                AdFieldCodec.map(entity.getJumpParams()),
                entity.weightOrZero(),
                AdDates.toInstant(entity.getStartTime()),
                AdDates.toInstant(entity.getEndTime()),
                entity.getStatus());
    }

    static int overlapCount(List<AdPlacementView> views) {
        int count = 0;
        for (int i = 0; i < views.size(); i++) {
            for (int j = i + 1; j < views.size(); j++) {
                AdPlacementView a = views.get(i);
                AdPlacementView b = views.get(j);
                if (AdDates.overlap(a.startTime(), a.endTime(), b.startTime(), b.endTime())) {
                    count++;
                }
            }
        }
        return count;
    }
}
