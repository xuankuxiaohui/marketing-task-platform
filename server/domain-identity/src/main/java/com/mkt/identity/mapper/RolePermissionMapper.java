package com.mkt.identity.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RolePermissionMapper {

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> listPermissionIds(@Param("roleId") long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") long permissionId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") long roleId, @Param("permissionId") long permissionId);

    @Select("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = #{permissionId}")
    int countByPermissionId(@Param("permissionId") long permissionId);

    @Select(
            """
            SELECT COUNT(*) FROM sys_role_permission rp
            INNER JOIN sys_role r ON r.id = rp.role_id
            WHERE rp.permission_id = #{permissionId} AND r.built_in = 1
            """)
    int countBuiltInBindings(@Param("permissionId") long permissionId);
}
