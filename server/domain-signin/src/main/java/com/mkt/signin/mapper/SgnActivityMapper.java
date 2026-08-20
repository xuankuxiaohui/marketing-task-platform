package com.mkt.signin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.signin.entity.SgnActivityEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SgnActivityMapper extends BaseMapper<SgnActivityEntity> {

    SgnActivityEntity selectByCode(@Param("code") String code);

    long selectCountByQuery(
            @Param("code") String code, @Param("name") String name, @Param("status") String status);

    List<SgnActivityEntity> selectByQuery(
            @Param("code") String code,
            @Param("name") String name,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    SgnActivityEntity selectByIdForUpdate(@Param("id") long id);

    List<SgnActivityEntity> selectPublished();

    List<SgnActivityEntity> selectDueScheduled(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    int casPublish(
            @Param("id") long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedPending") int expectedPending,
            @Param("nextVersion") int nextVersion,
            @Param("updatedAt") LocalDateTime updatedAt);
}
