package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.AdminUserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {

    @Select(
            """
            SELECT r.code FROM sys_role r
            INNER JOIN sys_admin_user_role ur ON ur.role_id = r.id
            WHERE ur.admin_user_id = #{userId} AND r.status = 'ENABLED'
            """)
    List<String> listRoleCodes(@Param("userId") long userId);

    @Select(
            """
            SELECT DISTINCT p.code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_admin_user_role ur ON ur.role_id = rp.role_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.admin_user_id = #{userId}
              AND r.status = 'ENABLED'
              AND p.type = 'OPERATION'
              AND p.code IS NOT NULL
            """)
    List<String> listPermissionCodes(@Param("userId") long userId);
}
