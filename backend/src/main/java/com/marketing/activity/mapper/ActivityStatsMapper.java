package com.marketing.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.activity.domain.entity.ActivityStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityStatsMapper extends BaseMapper<ActivityStats> {

    @Insert("INSERT INTO activity_stats (activity_code, stat_date, participant_count, completion_count, reward_count) " +
            "VALUES (#{activityCode}, CURDATE(), 1, 0, 0) " +
            "ON DUPLICATE KEY UPDATE participant_count = participant_count + 1")
    void incrementParticipantCount(@Param("activityCode") String activityCode);
}
