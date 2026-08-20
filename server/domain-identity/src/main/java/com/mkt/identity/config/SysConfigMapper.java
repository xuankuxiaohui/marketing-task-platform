package com.mkt.identity.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.SysConfigEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfigEntity> {

    @Select("SELECT * FROM sys_config WHERE config_key = #{configKey} LIMIT 1")
    SysConfigEntity getByKey(@Param("configKey") String configKey);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM sys_config
            WHERE 1 = 1
            <if test="configGroup != null">AND config_group = #{configGroup}</if>
            <if test="configKey != null">AND config_key = #{configKey}</if>
            </script>
            """)
    long countPage(@Param("configGroup") String configGroup, @Param("configKey") String configKey);

    @Select(
            """
            <script>
            SELECT * FROM sys_config
            WHERE 1 = 1
            <if test="configGroup != null">AND config_group = #{configGroup}</if>
            <if test="configKey != null">AND config_key = #{configKey}</if>
            ORDER BY id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SysConfigEntity> listPage(
            @Param("configGroup") String configGroup,
            @Param("configKey") String configKey,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
