package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.PrizeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrizeMapper extends BaseMapper<PrizeEntity> {

    PrizeEntity selectByCode(@Param("code") String code);

    long selectCountFiltered(
            @Param("code") String code,
            @Param("name") String name,
            @Param("categoryCode") String categoryCode,
            @Param("status") String status,
            @Param("groupId") Long groupId);

    List<PrizeEntity> selectPageFiltered(
            @Param("code") String code,
            @Param("name") String name,
            @Param("categoryCode") String categoryCode,
            @Param("status") String status,
            @Param("groupId") Long groupId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long selectCountByCategory(@Param("categoryCode") String categoryCode);

    int deductOne(@Param("id") long id);

    int restoreOne(@Param("id") long id);

    int replenish(@Param("id") long id, @Param("amount") int amount);

    int updateIgnoringRemainingStock(PrizeEntity entity);

    int resetDraftStock(@Param("id") long id);
}
