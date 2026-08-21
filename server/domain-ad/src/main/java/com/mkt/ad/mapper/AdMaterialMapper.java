package com.mkt.ad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.ad.entity.AdMaterialEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdMaterialMapper extends BaseMapper<AdMaterialEntity> {

    long selectCountByQuery(@Param("title") String title, @Param("status") String status);

    List<AdMaterialEntity> selectByQuery(
            @Param("title") String title,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    List<AdMaterialEntity> selectByIds(@Param("coll") List<Long> ids);
}
