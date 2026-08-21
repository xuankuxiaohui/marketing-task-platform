package com.mkt.ad.application;

import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.mapper.AdPositionMapper;
import com.mkt.ad.support.AdDuplicateKeys;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAdPositionStore implements AdPositionStore {

    private final AdPositionMapper mapper;

    public MybatisAdPositionStore(AdPositionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdPositionEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public AdPositionEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(AdPositionEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (AdDuplicateKeys.duplicate(ex)) {
                throw AdDuplicateKeys.wrap("uk_code", ex);
            }
            throw ex;
        }
    }

    @Override
    public int update(AdPositionEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long countByQuery(String code, String form, String status) {
        return mapper.selectCountByQuery(code, form, status);
    }

    @Override
    public List<AdPositionEntity> listByQuery(String code, String form, String status, long offset, int limit) {
        return mapper.selectByQuery(code, form, status, offset, limit);
    }
}
