package com.mkt.ad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdPositionMaterialMapper extends BaseMapper<AdPositionMaterialEntity> {

    List<AdPositionMaterialEntity> selectByPositionId(@Param("positionId") long positionId);

    AdPositionMaterialEntity selectByPositionAndMaterial(
            @Param("positionId") long positionId, @Param("materialId") long materialId);

    int deleteByPositionAndMaterial(@Param("positionId") long positionId, @Param("materialId") long materialId);

    int countByMaterialId(@Param("materialId") long materialId);
}
