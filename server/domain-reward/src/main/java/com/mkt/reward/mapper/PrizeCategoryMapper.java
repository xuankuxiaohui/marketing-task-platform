package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.PrizeCategoryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrizeCategoryMapper extends BaseMapper<PrizeCategoryEntity> {

    long selectCountAll();

    List<PrizeCategoryEntity> selectPage(@Param("offset") long offset, @Param("limit") int limit);
}
