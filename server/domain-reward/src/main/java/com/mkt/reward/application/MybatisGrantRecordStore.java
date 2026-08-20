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

    @Override
    public int casClaimStart(long id, LocalDateTime now, int retryMax) {
        return mapper.casClaimStart(id, now, retryMax);
    }

    @Override
    public int casExpireOne(long id, LocalDateTime now) {
        return mapper.casExpireOne(id, now);
    }

    @Override
    public int casPermanentFromRetry(long id, LocalDateTime now, int retryMax) {
        return mapper.casPermanentFromRetry(id, now, retryMax);
    }

    @Override
    public GrantRecordEntity getByFulfillmentRef(String fulfillmentRef) {
        return mapper.selectByFulfillmentRef(fulfillmentRef);
    }

    @Override
    public int updateIfFulfillment(GrantRecordEntity entity, String expectedFulfillment) {
        return mapper.updateIfFulfillment(entity, expectedFulfillment);
    }

    @Override
    public int closeAsManual(long id, LocalDateTime now) {
        return mapper.closeAsManual(id, now);
    }

    @Override
    public List<GrantRecordEntity> listClaimingTimeout(LocalDateTime cutoff, int limit) {
        return mapper.listClaimingTimeout(cutoff, limit);
    }

    @Override
    public int rollbackClaiming(long id, LocalDateTime now) {
        return mapper.rollbackClaiming(id, now);
    }

    @Override
    public List<GrantRecordEntity> listPrizeExpireDue(LocalDateTime now, int limit) {
        return mapper.listPrizeExpireDue(now, limit);
    }

    @Override
    public List<GrantRecordEntity> listFulfillRetryDue(LocalDateTime now, int limit) {
        return mapper.listFulfillRetryDue(now, limit);
    }

    @Override
    public List<GrantRecordEntity> listSendingTimeout(LocalDateTime cutoff, int limit) {
        return mapper.listSendingTimeout(cutoff, limit);
    }

    @Override
    public List<GrantRecordEntity> listCrossDaySending(LocalDateTime dayStart, int limit) {
        return mapper.listCrossDaySending(dayStart, limit);
    }

    @Override
    public int markReconPending(long id, LocalDateTime now) {
        return mapper.markReconPending(id, now);
    }

    @Override
    public List<GrantRecordEntity> listPlatformRecon(String categoryCode, LocalDateTime from, LocalDateTime to) {
        return mapper.listPlatformRecon(categoryCode, from, to);
    }

    @Override
    public List<GrantRecordEntity> listPortalPrizes(long userId, boolean pendingOnly, long offset, int limit) {
        return mapper.listPortalPrizes(userId, pendingOnly, offset, limit);
    }

    @Override
    public long countPortalPrizes(long userId, boolean pendingOnly) {
        return mapper.countPortalPrizes(userId, pendingOnly);
    }

    @Override
    public List<com.mkt.reward.response.SpendRowView> sumSpend(
            String categoryCode, Long prizeId, LocalDateTime from, LocalDateTime to) {
        return mapper.sumSpend(categoryCode, prizeId, from, to);
    }

    @Override
    public long countByUserStatus(long userId, String status) {
        return mapper.countByUserStatus(userId, status);
    }
}
