package com.mkt.signin.mapper;

import com.mkt.signin.entity.SgnRecordEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SgnRecordMapper {

    int insert(SgnRecordEntity entity);

    SgnRecordEntity selectByUk(
            @Param("activityId") long activityId, @Param("userId") long userId, @Param("signDate") LocalDate signDate);

    List<LocalDate> listSignDates(@Param("activityId") long activityId, @Param("userId") long userId);

    List<SgnRecordEntity> listInRange(
            @Param("activityId") long activityId,
            @Param("userId") long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    int countCatchupBetween(
            @Param("activityId") long activityId,
            @Param("userId") long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    long selectCountByQuery(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<SgnRecordEntity> selectByQuery(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
