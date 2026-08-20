package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.mapper.GrantRecordMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisGrantRecordStore implements GrantRecordStore {

    private final GrantRecordMapper mapper;

    public MybatisGrantRecordStore(GrantRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GrantRecordEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(GrantRecordEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public long countActive(long userId, long prizeId, LocalDateTime from) {
        return mapper.countActiveByUserPrize(userId, prizeId, from);
    }

    @Override
    public int markPermanentFailed(long id) {
        return mapper.markPermanentFailed(id);
    }
}
