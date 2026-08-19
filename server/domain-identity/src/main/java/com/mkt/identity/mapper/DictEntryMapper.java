package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.DictEntryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DictEntryMapper extends BaseMapper<DictEntryEntity> {

    @Select(
            """
            SELECT * FROM sys_dict_entry
            WHERE type_id = #{typeId} AND status = 'ENABLED'
            ORDER BY sort ASC, id ASC
            """)
    List<DictEntryEntity> listEnabledByTypeId(@Param("typeId") long typeId);

    @Select("SELECT COUNT(*) FROM sys_dict_entry WHERE type_id = #{typeId}")
    int countByTypeId(@Param("typeId") long typeId);

    @Select(
            """
            SELECT * FROM sys_dict_entry
            WHERE type_id = #{typeId} AND value = #{value}
            LIMIT 1
            """)
    DictEntryEntity getByTypeIdAndValue(@Param("typeId") long typeId, @Param("value") String value);
}
