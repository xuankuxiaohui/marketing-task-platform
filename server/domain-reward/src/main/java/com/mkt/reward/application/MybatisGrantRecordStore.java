package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.mapper.GrantRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
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
    public GrantRecordEntity getByIdempotent(String grantSource, String sourceId, long prizeId) {
        return mapper.selectByIdempotent(grantSource, sourceId, prizeId);
    }

    @Override
    public int insert(GrantRecordEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(GrantRecordEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int updateIfStatus(GrantRecordEntity entity, String expectedStatus) {
        return mapper.updateIfStatus(entity, expectedStatus);
    }

    @Override
    public int updateFulfillmentRef(long id, String fulfillmentRef) {
        return mapper.updateFulfillmentRef(id, fulfillmentRef);
    }

    @Override
    public long countActive(long userId, long prizeId, LocalDateTime from) {
        return mapper.countActiveByUserPrize(userId, prizeId, from);
    }

    @Override
    public int markPermanentFailed(long id) {
        return mapper.markPermanentFailed(id);
    }

    @Override
    public List<GrantRecordEntity> listDueRetry(LocalDateTime now, int limit) {
        return mapper.listDueRetry(now, limit);
    }
}
