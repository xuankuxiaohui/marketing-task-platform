package com.mkt.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.identity.entity.PortalUserEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortalUserMapper extends BaseMapper<PortalUserEntity> {

    @Select("SELECT * FROM sys_portal_user WHERE id = #{id} FOR UPDATE")
    PortalUserEntity lockById(@Param("id") long id);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM sys_portal_user
            WHERE deleted = 0
            <if test="username != null">AND username LIKE CONCAT('%', #{username}, '%') ESCAPE '\\\\'</if>
            <if test="nickname != null">AND nickname LIKE CONCAT('%', #{nickname}, '%') ESCAPE '\\\\'</if>
            <if test="province != null">AND province = #{province}</if>
            <if test="level != null">AND user_level = #{level}</if>
            <if test="tag != null">AND JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="registeredFrom != null">AND registered_at &gt;= #{registeredFrom}</if>
            <if test="registeredTo != null">AND registered_at &lt;= #{registeredTo}</if>
            </script>
            """)
    long countPage(
            @Param("username") String username,
            @Param("nickname") String nickname,
            @Param("province") String province,
            @Param("level") String level,
            @Param("tag") String tag,
            @Param("status") String status,
            @Param("registeredFrom") LocalDateTime registeredFrom,
            @Param("registeredTo") LocalDateTime registeredTo);

    @Select(
            """
            <script>
            SELECT * FROM sys_portal_user
            WHERE deleted = 0
            <if test="username != null">AND username LIKE CONCAT('%', #{username}, '%') ESCAPE '\\\\'</if>
            <if test="nickname != null">AND nickname LIKE CONCAT('%', #{nickname}, '%') ESCAPE '\\\\'</if>
            <if test="province != null">AND province = #{province}</if>
            <if test="level != null">AND user_level = #{level}</if>
            <if test="tag != null">AND JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="registeredFrom != null">AND registered_at &gt;= #{registeredFrom}</if>
            <if test="registeredTo != null">AND registered_at &lt;= #{registeredTo}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PortalUserEntity> listPage(
            @Param("username") String username,
            @Param("nickname") String nickname,
            @Param("province") String province,
            @Param("level") String level,
            @Param("tag") String tag,
            @Param("status") String status,
            @Param("registeredFrom") LocalDateTime registeredFrom,
            @Param("registeredTo") LocalDateTime registeredTo,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
