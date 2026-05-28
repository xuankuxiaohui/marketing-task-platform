package com.marketing.task.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.activity.domain.entity.ActivityStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityStatsMapper extends BaseMapper<ActivityStats> {
}
