package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.UserTaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserTaskInstanceMapper extends BaseMapper<UserTaskInstance> {

    @Select("<script>SELECT task_id, COUNT(1) AS cnt FROM user_task_instance WHERE task_id IN <foreach item='id' collection='taskIds' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY task_id</script>")
    List<Map<String, Object>> countByTaskIds(@Param("taskIds") java.util.Collection<Long> taskIds);
}
