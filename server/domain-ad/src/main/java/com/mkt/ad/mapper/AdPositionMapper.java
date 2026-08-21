package com.mkt.ad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.ad.entity.AdPositionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdPositionMapper extends BaseMapper<AdPositionEntity> {

    AdPositionEntity selectByCode(@Param("code") String code);

    long selectCountByQuery(
            @Param("code") String code, @Param("form") String form, @Param("status") String status);

    List<AdPositionEntity> selectByQuery(
            @Param("code") String code,
            @Param("form") String form,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
