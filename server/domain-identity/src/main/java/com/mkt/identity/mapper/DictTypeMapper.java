package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.DictTypeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeEntity> {

    @Select("SELECT * FROM sys_dict_type WHERE code = #{code} LIMIT 1")
    DictTypeEntity getByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM sys_dict_type")
    long countAll();

    @Select("SELECT * FROM sys_dict_type ORDER BY id ASC LIMIT #{limit} OFFSET #{offset}")
    List<DictTypeEntity> listPage(@Param("offset") long offset, @Param("limit") int limit);
}
