package com.mkt.ad.application;

import com.mkt.ad.entity.AdMaterialEntity;
import java.util.List;

public interface AdMaterialStore {

    AdMaterialEntity getById(long id);

    int insert(AdMaterialEntity entity);

    int update(AdMaterialEntity entity);

    long countByQuery(String title, String status);

    List<AdMaterialEntity> listByQuery(String title, String status, long offset, int limit);

    List<AdMaterialEntity> listByIds(List<Long> ids);
}
