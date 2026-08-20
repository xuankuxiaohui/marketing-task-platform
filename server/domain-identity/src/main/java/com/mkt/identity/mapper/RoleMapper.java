package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.RoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select(
            """
            SELECT r.id, r.code, r.name, r.description, r.status, r.built_in, r.created_at, r.updated_at
            FROM sys_role r
            INNER JOIN sys_admin_user_role ur ON ur.role_id = r.id
            WHERE ur.admin_user_id = #{userId}
            """)
    List<RoleEntity> listByUserId(@Param("userId") long userId);

    @Select(
            """
            SELECT COUNT(*) FROM sys_role r
            INNER JOIN sys_admin_user_role ur ON ur.role_id = r.id
            WHERE ur.admin_user_id = #{userId} AND r.built_in = 1 AND r.status = 'ENABLED'
            """)
    int countEnabledBuiltInByUserId(@Param("userId") long userId);

    @Select("SELECT * FROM sys_role WHERE code = #{code} LIMIT 1")
    RoleEntity getByCode(@Param("code") String code);

    @Select("SELECT * FROM sys_role WHERE status = 'ENABLED' ORDER BY id ASC")
    List<RoleEntity> listEnabled();

    @Select("SELECT * FROM sys_role ORDER BY id ASC LIMIT #{limit} OFFSET #{offset}")
    List<RoleEntity> listPage(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM sys_role")
    long countAll();
}
