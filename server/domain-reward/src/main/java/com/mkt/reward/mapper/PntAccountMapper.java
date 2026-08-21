package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.PntAccountEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PntAccountMapper extends BaseMapper<PntAccountEntity> {

    int insertIgnore(@Param("userId") long userId);

    PntAccountEntity selectByUserId(@Param("userId") long userId);

    PntAccountEntity selectByUserIdForUpdate(@Param("userId") long userId);

    int applyDelta(@Param("userId") long userId, @Param("delta") long delta);

    long selectCountFiltered(@Param("userId") Long userId);

    List<PntAccountEntity> selectPageFiltered(
            @Param("userId") Long userId, @Param("offset") long offset, @Param("limit") int limit);
}
