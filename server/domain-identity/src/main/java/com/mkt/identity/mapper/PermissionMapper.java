package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.PermissionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    @Select("SELECT * FROM sys_permission ORDER BY sort ASC, id ASC")
    List<PermissionEntity> listAllOrdered();

    @Select(
            """
            SELECT DISTINCT p.code FROM sys_permission p
            WHERE p.type = 'OPERATION' AND p.status = 'ENABLED' AND p.code IS NOT NULL
            """)
    List<String> listEnabledOperationCodes();

    @Select(
            """
            SELECT DISTINCT p.id FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_admin_user_role ur ON ur.role_id = rp.role_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.admin_user_id = #{userId}
              AND r.status = 'ENABLED'
              AND p.status = 'ENABLED'
            """)
    List<Long> listHeldIdsByUserId(@Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM sys_permission WHERE parent_id = #{parentId}")
    int countByParentId(@Param("parentId") long parentId);
}
