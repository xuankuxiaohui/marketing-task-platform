package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskMutexGroupEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMutexGroupMapper extends BaseMapper<TaskMutexGroupEntity> {

    TaskMutexGroupEntity selectByCode(@Param("code") String code);

    long selectCountAll();

    List<TaskMutexGroupEntity> selectPage(@Param("offset") long offset, @Param("limit") int limit);
}
