package com.mkt.signin.mapper;

import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Insert/select only (RL-12). */
@Mapper
public interface SgnActivitySnapshotMapper {

    int insert(SgnActivitySnapshotEntity entity);

    SgnActivitySnapshotEntity selectById(@Param("id") long id);

    SgnActivitySnapshotEntity selectByActivityAndVersion(
            @Param("activityId") long activityId, @Param("version") int version);

    List<SgnActivitySnapshotEntity> selectByActivityId(@Param("activityId") long activityId);
}
