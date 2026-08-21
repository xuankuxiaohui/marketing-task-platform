package com.mkt.ad.application;

import com.mkt.ad.entity.AdPositionMaterialEntity;
import java.util.List;

public interface AdPlacementStore {

    List<AdPositionMaterialEntity> listByPositionId(long positionId);

    AdPositionMaterialEntity getByPositionAndMaterial(long positionId, long materialId);

    int insert(AdPositionMaterialEntity entity);

    int update(AdPositionMaterialEntity entity);

    int delete(long positionId, long materialId);

    int countByMaterialId(long materialId);
}
