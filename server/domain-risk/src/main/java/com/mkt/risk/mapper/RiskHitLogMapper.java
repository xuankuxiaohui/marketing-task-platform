package com.mkt.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.risk.entity.RiskHitLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskHitLogMapper extends BaseMapper<RiskHitLogEntity> {

    long selectCountByQuery(
            @Param("ruleCode") String ruleCode,
            @Param("hitType") String hitType,
            @Param("dimensionValue") String dimensionValue,
            @Param("userId") Long userId,
            @Param("actionResult") String actionResult,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<RiskHitLogEntity> selectListByQuery(
            @Param("ruleCode") String ruleCode,
            @Param("hitType") String hitType,
            @Param("dimensionValue") String dimensionValue,
            @Param("userId") Long userId,
            @Param("actionResult") String actionResult,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
