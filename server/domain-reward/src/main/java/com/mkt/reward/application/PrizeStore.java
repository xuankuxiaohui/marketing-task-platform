package com.mkt.reward.application;

import com.mkt.reward.entity.PrizeEntity;
import java.util.List;

public interface PrizeStore {

    PrizeEntity getById(long id);

    PrizeEntity getByCode(String code);

    int insert(PrizeEntity entity);

    int update(PrizeEntity entity);

    long count(
            String code, String name, String categoryCode, String status, Long groupId);

    List<PrizeEntity> list(
            String code,
            String name,
            String categoryCode,
            String status,
            Long groupId,
            long offset,
            int limit);

    long countByCategory(String categoryCode);

    int deductOne(long id);

    int restoreOne(long id);

    int replenish(long id, int amount);
}
