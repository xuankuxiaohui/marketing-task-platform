package com.mkt.reward.application;

import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.mapper.PrizeCategoryMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPrizeCategoryStore implements PrizeCategoryStore {

    private final PrizeCategoryMapper mapper;

    public MybatisPrizeCategoryStore(PrizeCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PrizeCategoryEntity getByCode(String code) {
        return mapper.selectById(code);
    }

    @Override
    public int insert(PrizeCategoryEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(PrizeCategoryEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteByCode(String code) {
        return mapper.deleteById(code);
    }

    @Override
    public long countAll() {
        return mapper.selectCountAll();
    }

    @Override
    public List<PrizeCategoryEntity> list(long offset, int limit) {
        return mapper.selectPage(offset, limit);
    }
}
