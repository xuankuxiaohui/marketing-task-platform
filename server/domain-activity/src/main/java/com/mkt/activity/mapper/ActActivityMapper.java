package com.mkt.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.activity.entity.ActActivityEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActActivityMapper extends BaseMapper<ActActivityEntity> {

    ActActivityEntity selectByCode(@Param("code") String code);

    long selectCountByQuery(
            @Param("code") String code, @Param("name") String name, @Param("status") String status);

    List<ActActivityEntity> selectByQuery(
            @Param("code") String code,
            @Param("name") String name,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    ActActivityEntity selectByIdForUpdate(@Param("id") long id);

    List<ActActivityEntity> selectPublished();

    List<ActActivityEntity> selectDueScheduled(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    List<ActActivityEntity> selectDueOffline(
            @Param("now") LocalDateTime now, @Param("limit") int limit);
}
