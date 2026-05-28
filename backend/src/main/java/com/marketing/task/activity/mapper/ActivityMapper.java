package com.marketing.task.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.activity.domain.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
