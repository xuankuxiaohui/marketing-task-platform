package com.mkt.signin.application;

import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import com.mkt.signin.mapper.SgnActivitySnapshotMapper;
import com.mkt.signin.support.SigninDuplicateKeys;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisSigninSnapshotStore implements SigninSnapshotStore {

    private final SgnActivitySnapshotMapper mapper;

    public MybatisSigninSnapshotStore(SgnActivitySnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(SgnActivitySnapshotEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (SigninDuplicateKeys.duplicate(ex)) {
                throw SigninDuplicateKeys.wrap("uk_activity_version", ex);
            }
            throw ex;
        }
    }

    @Override
    public SgnActivitySnapshotEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public SgnActivitySnapshotEntity getByActivityAndVersion(long activityId, int version) {
        return mapper.selectByActivityAndVersion(activityId, version);
    }

    @Override
    public List<SgnActivitySnapshotEntity> listByActivityId(long activityId) {
        return mapper.selectByActivityId(activityId);
    }
}
