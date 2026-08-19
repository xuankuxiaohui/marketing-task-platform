package com.mkt.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.risk.application.RiskListUk;
import com.mkt.risk.entity.RiskListItemEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskListItemMapper extends BaseMapper<RiskListItemEntity> {

    RiskListItemEntity getByUk(
            @Param("dimension") String dimension,
            @Param("listType") String listType,
            @Param("listValue") String listValue);

    List<RiskListItemEntity> listByUks(@Param("uks") List<RiskListUk> uks);

    long countByQuery(
            @Param("dimension") String dimension,
            @Param("listType") String listType,
            @Param("listValue") String listValue,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<RiskListItemEntity> listByQuery(
            @Param("dimension") String dimension,
            @Param("listType") String listType,
            @Param("listValue") String listValue,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
