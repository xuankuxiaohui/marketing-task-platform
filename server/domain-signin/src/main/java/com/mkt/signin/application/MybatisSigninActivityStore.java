package com.mkt.signin.application;

import com.mkt.signin.entity.SgnActivityEntity;
import com.mkt.signin.mapper.SgnActivityMapper;
import com.mkt.signin.support.SigninDuplicateKeys;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisSigninActivityStore implements SigninActivityStore {

    private final SgnActivityMapper mapper;

    public MybatisSigninActivityStore(SgnActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SgnActivityEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public SgnActivityEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(SgnActivityEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (SigninDuplicateKeys.duplicate(ex)) {
                throw SigninDuplicateKeys.wrap("uk_code", ex);
            }
            throw ex;
        }
    }

    @Override
    public int update(SgnActivityEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long countByQuery(String code, String name, String status) {
        return mapper.selectCountByQuery(code, name, status);
    }

    @Override
    public List<SgnActivityEntity> listByQuery(String code, String name, String status, long offset, int limit) {
        return mapper.selectByQuery(code, name, status, offset, limit);
    }

    @Override
    public SgnActivityEntity getByIdForUpdate(long id) {
        return mapper.selectByIdForUpdate(id);
    }

    @Override
    public List<SgnActivityEntity> listPublished() {
        return mapper.selectPublished();
    }

    @Override
    public List<SgnActivityEntity> listDueScheduled(LocalDateTime now, int limit) {
        return mapper.selectDueScheduled(now, limit);
    }

    @Override
    public int casPublish(
            long id,
            String expectedStatus,
            int expectedVersion,
            int expectedPending,
            int nextVersion,
            LocalDateTime updatedAt) {
        return mapper.casPublish(id, expectedStatus, expectedVersion, expectedPending, nextVersion, updatedAt);
    }
}
