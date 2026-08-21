package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.ReconBatchEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReconBatchMapper extends BaseMapper<ReconBatchEntity> {

    ReconBatchEntity selectByCategoryAndDay(
            @Param("categoryCode") String categoryCode, @Param("billDate") LocalDate billDate);

    List<ReconBatchEntity> listByFilter(
            @Param("categoryCode") String categoryCode,
            @Param("billDate") LocalDate billDate,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countByFilter(
            @Param("categoryCode") String categoryCode,
            @Param("billDate") LocalDate billDate,
            @Param("status") String status);
}
