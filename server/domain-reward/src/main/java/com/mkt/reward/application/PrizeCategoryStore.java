package com.mkt.reward.application;

import com.mkt.reward.entity.PrizeCategoryEntity;
import java.util.List;

public interface PrizeCategoryStore {

    PrizeCategoryEntity getByCode(String code);

    int insert(PrizeCategoryEntity entity);

    int update(PrizeCategoryEntity entity);

    int deleteByCode(String code);

    long countAll();

    List<PrizeCategoryEntity> list(long offset, int limit);
}
