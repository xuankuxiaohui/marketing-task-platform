package com.marketing.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.system.domain.entity.EventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventLogMapper extends BaseMapper<EventLog> {
}
