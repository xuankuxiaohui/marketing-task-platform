package com.mkt.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.tracking.entity.EvtEventLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvtEventLogMapper extends BaseMapper<EvtEventLogEntity> {

    long selectCountByQuery(
            @Param("eventCode") String eventCode,
            @Param("userId") Long userId,
            @Param("source") String source,
            @Param("deviceId") String deviceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("sampleRatioPercent") int sampleRatioPercent);

    List<EvtEventLogEntity> selectByQuery(
            @Param("eventCode") String eventCode,
            @Param("userId") Long userId,
            @Param("source") String source,
            @Param("deviceId") String deviceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("sampleRatioPercent") int sampleRatioPercent,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
