package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.InternalAppEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InternalAppMapper extends BaseMapper<InternalAppEntity> {

    @Select("SELECT COUNT(*) FROM sys_internal_app")
    long countAll();

    @Select("SELECT * FROM sys_internal_app ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<InternalAppEntity> listPage(@Param("offset") long offset, @Param("limit") int limit);
}
