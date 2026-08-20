package com.mkt.signin.application;

import com.mkt.signin.entity.SgnActivityEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface SigninActivityStore {

    SgnActivityEntity getById(long id);

    SgnActivityEntity getByCode(String code);

    int insert(SgnActivityEntity entity);

    int update(SgnActivityEntity entity);

    long countByQuery(String code, String name, String status);

    List<SgnActivityEntity> listByQuery(String code, String name, String status, long offset, int limit);

    SgnActivityEntity getByIdForUpdate(long id);

    List<SgnActivityEntity> listPublished();

    List<SgnActivityEntity> listDueScheduled(LocalDateTime now, int limit);

    int casPublish(
            long id, String expectedStatus, int expectedVersion, int expectedPending, int nextVersion, LocalDateTime updatedAt);
}
