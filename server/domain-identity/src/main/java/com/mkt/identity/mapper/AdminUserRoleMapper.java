package com.mkt.identity.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserRoleMapper {

    @Delete("DELETE FROM sys_admin_user_role WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Select("SELECT COUNT(*) FROM sys_admin_user_role WHERE role_id = #{roleId}")
    int countUsers(@Param("roleId") long roleId);

    @Select("SELECT admin_user_id FROM sys_admin_user_role WHERE role_id = #{roleId}")
    List<Long> listUserIds(@Param("roleId") long roleId);

    @Select("SELECT role_id FROM sys_admin_user_role WHERE admin_user_id = #{userId}")
    List<Long> listRoleIds(@Param("userId") long userId);

    @Insert("INSERT INTO sys_admin_user_role (admin_user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") long userId, @Param("roleId") long roleId);

    @Delete("DELETE FROM sys_admin_user_role WHERE admin_user_id = #{userId}")
    int deleteByUserId(@Param("userId") long userId);
}
