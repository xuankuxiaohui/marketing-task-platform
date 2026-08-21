package com.mkt.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.HitRuleCountView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActParticipationMapper extends BaseMapper<ActParticipationEntity> {

    int countPass(@Param("activityId") long activityId, @Param("userId") Long userId, @Param("periodKey") String periodKey);

    long selectCountByQuery(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId,
            @Param("result") String result,
            @Param("periodKey") String periodKey);

    List<ActParticipationEntity> selectByQuery(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId,
            @Param("result") String result,
            @Param("periodKey") String periodKey,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countAll(@Param("activityId") long activityId);

    long countPassByActivity(@Param("activityId") long activityId);

    List<HitRuleCountView> countRejectByRule(@Param("activityId") long activityId);
}
