package com.mkt.reward.application;

import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.mapper.StockLogMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisStockLogStore implements StockLogStore {

    private final StockLogMapper mapper;

    public MybatisStockLogStore(StockLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(StockLogEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public long countByPrize(long prizeId) {
        return mapper.selectCountByPrize(prizeId);
    }

    @Override
    public List<StockLogEntity> listByPrize(long prizeId, long offset, int limit) {
        return mapper.selectPageByPrize(prizeId, offset, limit);
    }
}
