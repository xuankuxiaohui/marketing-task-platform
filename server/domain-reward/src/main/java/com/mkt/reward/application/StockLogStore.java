package com.mkt.reward.application;

import com.mkt.reward.entity.StockLogEntity;
import java.util.List;

public interface StockLogStore {

    int insert(StockLogEntity entity);

    long countByPrize(long prizeId);

    List<StockLogEntity> listByPrize(long prizeId, long offset, int limit);
}
