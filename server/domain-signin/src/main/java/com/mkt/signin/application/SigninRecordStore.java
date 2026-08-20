package com.mkt.signin.application;

import com.mkt.signin.entity.SgnRecordEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SigninRecordStore {

    int insert(SgnRecordEntity entity);

    SgnRecordEntity getByUk(long activityId, long userId, LocalDate signDate);

    List<LocalDate> listSignDates(long activityId, long userId);

    List<SgnRecordEntity> listInRange(long activityId, long userId, LocalDate from, LocalDate to);

    int countCatchupBetween(long activityId, long userId, LocalDateTime from, LocalDateTime to);

    long countByQuery(Long activityId, Long userId, LocalDate from, LocalDate to);

    List<SgnRecordEntity> listByQuery(
            Long activityId, Long userId, LocalDate from, LocalDate to, long offset, int limit);
}
