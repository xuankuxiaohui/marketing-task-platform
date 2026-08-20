package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskCrowdEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskCrowdMapper extends BaseMapper<TaskCrowdEntity> {

    TaskCrowdEntity selectByCode(@Param("code") String code);

    long selectCountAll();

    List<TaskCrowdEntity> selectPage(@Param("offset") long offset, @Param("limit") int limit);
}
