package com.mkt.ad.application;

import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.mapper.AdMaterialMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAdMaterialStore implements AdMaterialStore {

    private final AdMaterialMapper mapper;

    public MybatisAdMaterialStore(AdMaterialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdMaterialEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(AdMaterialEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(AdMaterialEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long countByQuery(String title, String status) {
        return mapper.selectCountByQuery(title, status);
    }

    @Override
    public List<AdMaterialEntity> listByQuery(String title, String status, long offset, int limit) {
        return mapper.selectByQuery(title, status, offset, limit);
    }

    @Override
    public List<AdMaterialEntity> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }
}
