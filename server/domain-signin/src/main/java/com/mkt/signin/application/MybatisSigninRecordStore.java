package com.mkt.signin.application;

import com.mkt.signin.entity.SgnRecordEntity;
import com.mkt.signin.mapper.SgnRecordMapper;
import com.mkt.signin.support.SigninDuplicateKeys;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisSigninRecordStore implements SigninRecordStore {

    private final SgnRecordMapper mapper;

    public MybatisSigninRecordStore(SgnRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(SgnRecordEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (SigninDuplicateKeys.duplicate(ex)) {
                throw SigninDuplicateKeys.wrap("uk_activity_user_date", ex);
            }
            throw ex;
        }
    }

    @Override
    public SgnRecordEntity getByUk(long activityId, long userId, LocalDate signDate) {
        return mapper.selectByUk(activityId, userId, signDate);
    }

    @Override
    public List<LocalDate> listSignDates(long activityId, long userId) {
        return mapper.listSignDates(activityId, userId);
    }

    @Override
    public List<SgnRecordEntity> listInRange(long activityId, long userId, LocalDate from, LocalDate to) {
        return mapper.listInRange(activityId, userId, from, to);
    }

    @Override
    public int countCatchupBetween(long activityId, long userId, LocalDateTime from, LocalDateTime to) {
        return mapper.countCatchupBetween(activityId, userId, from, to);
    }

    @Override
    public long countByQuery(Long activityId, Long userId, LocalDate from, LocalDate to) {
        return mapper.selectCountByQuery(activityId, userId, from, to);
    }

    @Override
    public List<SgnRecordEntity> listByQuery(
            Long activityId, Long userId, LocalDate from, LocalDate to, long offset, int limit) {
        return mapper.selectByQuery(activityId, userId, from, to, offset, limit);
    }
}
