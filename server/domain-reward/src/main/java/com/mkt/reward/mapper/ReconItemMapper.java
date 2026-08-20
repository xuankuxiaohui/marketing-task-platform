package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.ReconItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReconItemMapper extends BaseMapper<ReconItemEntity> {

    List<ReconItemEntity> listByBatch(
            @Param("batchId") long batchId,
            @Param("result") String result,
            @Param("reviewStatus") String reviewStatus,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countByBatch(
            @Param("batchId") long batchId,
            @Param("result") String result,
            @Param("reviewStatus") String reviewStatus);

    List<ReconItemEntity> listAllByBatch(@Param("batchId") long batchId);

    int casAction(@Param("row") ReconItemEntity row);

    int casReview(@Param("row") ReconItemEntity row);
}
