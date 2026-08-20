package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GrantRecordMapper extends BaseMapper<GrantRecordEntity> {

    long countActiveByUserPrize(
            @Param("userId") long userId,
            @Param("prizeId") long prizeId,
            @Param("from") LocalDateTime from);

    int markPermanentFailed(@Param("id") long id);
}
