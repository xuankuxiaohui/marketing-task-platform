package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.StockLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockLogMapper extends BaseMapper<StockLogEntity> {

    long selectCountByPrize(@Param("prizeId") long prizeId);

    List<StockLogEntity> selectPageByPrize(
            @Param("prizeId") long prizeId, @Param("offset") long offset, @Param("limit") int limit);
}
