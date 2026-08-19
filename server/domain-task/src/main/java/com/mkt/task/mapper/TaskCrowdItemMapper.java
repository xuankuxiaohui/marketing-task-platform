package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskCrowdItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskCrowdItemMapper extends BaseMapper<TaskCrowdItemEntity> {

    int countByCrowdAndUser(@Param("crowdId") long crowdId, @Param("userId") long userId);

    int insertIgnore(@Param("crowdId") long crowdId, @Param("userId") long userId);

    int deleteByCrowdId(@Param("crowdId") long crowdId);

    List<Long> selectUserIds(@Param("crowdId") long crowdId, @Param("offset") long offset, @Param("limit") int limit);
}
