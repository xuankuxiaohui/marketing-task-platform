package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    @Select("<script>SELECT * FROM task WHERE deleted = 1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (code LIKE CONCAT('%',#{keyword},'%') OR name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='periodType != null and periodType != \"\"'> AND period_type = #{periodType} </if>" +
            " ORDER BY id DESC LIMIT #{offset}, #{size}</script>")
    List<Task> selectDeletedPage(@Param("offset") long offset,
                                 @Param("size") long size,
                                 @Param("status") String status,
                                 @Param("keyword") String keyword,
                                 @Param("periodType") String periodType);

    @Select("<script>SELECT COUNT(*) FROM task WHERE deleted = 1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (code LIKE CONCAT('%',#{keyword},'%') OR name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='periodType != null and periodType != \"\"'> AND period_type = #{periodType} </if>" +
            "</script>")
    long countDeleted(@Param("status") String status,
                      @Param("keyword") String keyword,
                      @Param("periodType") String periodType);
}
