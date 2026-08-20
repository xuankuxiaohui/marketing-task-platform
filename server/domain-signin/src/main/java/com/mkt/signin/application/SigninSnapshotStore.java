package com.mkt.signin.application;

import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import java.util.List;

public interface SigninSnapshotStore {

    int insert(SgnActivitySnapshotEntity entity);

    SgnActivitySnapshotEntity getById(long id);

    SgnActivitySnapshotEntity getByActivityAndVersion(long activityId, int version);

    List<SgnActivitySnapshotEntity> listByActivityId(long activityId);
}
