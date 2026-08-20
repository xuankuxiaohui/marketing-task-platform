package com.mkt.reward.application;

import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.mapper.PrizeMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPrizeStore implements PrizeStore {

    private final PrizeMapper mapper;

    public MybatisPrizeStore(PrizeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PrizeEntity getById(long id) {
        PrizeEntity entity = mapper.selectById(id);
        if (entity == null || entity.deletedFlag()) {
            return null;
        }
        return entity;
    }

    @Override
    public PrizeEntity getByIdIncludingDeleted(long id) {
        return mapper.selectById(id);
    }

    @Override
    public PrizeEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(PrizeEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(PrizeEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long count(String code, String name, String categoryCode, String status, Long groupId) {
        return mapper.selectCountFiltered(code, name, categoryCode, status, groupId);
    }

    @Override
    public List<PrizeEntity> list(
            String code,
            String name,
            String categoryCode,
            String status,
            Long groupId,
            long offset,
            int limit) {
        return mapper.selectPageFiltered(code, name, categoryCode, status, groupId, offset, limit);
    }

    @Override
    public long countByCategory(String categoryCode) {
        return mapper.selectCountByCategory(categoryCode);
    }

    @Override
    public int deductOne(long id) {
        return mapper.deductOne(id);
    }

    @Override
    public int restoreOne(long id) {
        return mapper.restoreOne(id);
    }

    @Override
    public int replenish(long id, int amount) {
        return mapper.replenish(id, amount);
    }
}
