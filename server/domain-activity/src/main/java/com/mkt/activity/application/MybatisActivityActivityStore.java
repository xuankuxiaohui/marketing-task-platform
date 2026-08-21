package com.mkt.activity.application;

import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.mapper.ActActivityMapper;
import com.mkt.activity.support.ActivityDuplicateKeys;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisActivityActivityStore implements ActivityActivityStore {

    private final ActActivityMapper mapper;

    public MybatisActivityActivityStore(ActActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ActActivityEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public ActActivityEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(ActActivityEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (ActivityDuplicateKeys.duplicate(ex)) {
                throw ActivityDuplicateKeys.wrap("uk_code", ex);
            }
            throw ex;
        }
    }

    @Override
    public int update(ActActivityEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long countByQuery(String code, String name, String status) {
        return mapper.selectCountByQuery(code, name, status);
    }

    @Override
    public List<ActActivityEntity> listByQuery(String code, String name, String status, long offset, int limit) {
        return mapper.selectByQuery(code, name, status, offset, limit);
    }

    @Override
    public ActActivityEntity getByIdForUpdate(long id) {
        return mapper.selectByIdForUpdate(id);
    }

    @Override
    public List<ActActivityEntity> listPublished() {
        return mapper.selectPublished();
    }

    @Override
    public List<ActActivityEntity> listDueScheduled(LocalDateTime now, int limit) {
        return mapper.selectDueScheduled(now, limit);
    }

    @Override
    public List<ActActivityEntity> listDueOffline(LocalDateTime now, int limit) {
        return mapper.selectDueOffline(now, limit);
    }
}
