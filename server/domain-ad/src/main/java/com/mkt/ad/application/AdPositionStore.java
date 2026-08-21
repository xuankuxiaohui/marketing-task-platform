package com.mkt.ad.application;

import com.mkt.ad.entity.AdPositionEntity;
import java.util.List;

public interface AdPositionStore {

    AdPositionEntity getById(long id);

    AdPositionEntity getByCode(String code);

    int insert(AdPositionEntity entity);

    int update(AdPositionEntity entity);

    long countByQuery(String code, String form, String status);

    List<AdPositionEntity> listByQuery(String code, String form, String status, long offset, int limit);
}
