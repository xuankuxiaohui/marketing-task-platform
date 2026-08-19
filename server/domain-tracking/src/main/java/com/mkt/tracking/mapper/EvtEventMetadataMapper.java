package com.mkt.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvtEventMetadataMapper extends BaseMapper<EvtEventMetadataEntity> {

    @Select("SELECT id, event_code, name, status FROM evt_event_metadata WHERE event_code = #{eventCode}")
    EvtEventMetadataEntity selectByEventCode(@Param("eventCode") String eventCode);
}
