package com.mkt.activity.application;

import com.mkt.activity.entity.ActActivityEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityActivityStore {

    ActActivityEntity getById(long id);

    ActActivityEntity getByCode(String code);

    int insert(ActActivityEntity entity);

    int update(ActActivityEntity entity);

    long countByQuery(String code, String name, String status);

    List<ActActivityEntity> listByQuery(String code, String name, String status, long offset, int limit);

    ActActivityEntity getByIdForUpdate(long id);

    List<ActActivityEntity> listPublished();

    List<ActActivityEntity> listDueScheduled(LocalDateTime now, int limit);

    List<ActActivityEntity> listDueOffline(LocalDateTime now, int limit);
}
