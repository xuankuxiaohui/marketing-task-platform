package com.mkt.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvtEventMetadataMapper extends BaseMapper<EvtEventMetadataEntity> {

    @Select("SELECT id, event_code, name, prop_schema, status, owner, remark, created_at, updated_at"
            + " FROM evt_event_metadata WHERE event_code = #{eventCode}")
    EvtEventMetadataEntity selectByEventCode(@Param("eventCode") String eventCode);

    long selectCountByQuery(@Param("eventCode") String eventCode, @Param("status") String status);

    List<EvtEventMetadataEntity> selectByQuery(
            @Param("eventCode") String eventCode,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
