package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GrantRecordMapper extends BaseMapper<GrantRecordEntity> {

    long countActiveByUserPrize(
            @Param("userId") long userId,
            @Param("prizeId") long prizeId,
            @Param("from") LocalDateTime from);

    int markPermanentFailed(@Param("id") long id);

    GrantRecordEntity selectByIdempotent(
            @Param("grantSource") String grantSource,
            @Param("sourceId") String sourceId,
            @Param("prizeId") long prizeId);

    int updateIfStatus(
            @Param("row") GrantRecordEntity row, @Param("expectedStatus") String expectedStatus);

    int updateFulfillmentRef(@Param("id") long id, @Param("fulfillmentRef") String fulfillmentRef);

    List<GrantRecordEntity> listDueRetry(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
