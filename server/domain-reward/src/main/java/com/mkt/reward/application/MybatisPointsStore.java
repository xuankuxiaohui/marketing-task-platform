package com.mkt.reward.application;

import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import com.mkt.reward.mapper.PntAccountMapper;
import com.mkt.reward.mapper.PntTransactionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPointsStore implements PointsStore {

    private final PntAccountMapper accounts;
    private final PntTransactionMapper transactions;

    public MybatisPointsStore(PntAccountMapper accounts, PntTransactionMapper transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Override
    public int insertIgnoreAccount(long userId) {
        return accounts.insertIgnore(userId);
    }

    @Override
    public PntAccountEntity getByUserId(long userId) {
        return accounts.selectByUserId(userId);
    }

    @Override
    public PntAccountEntity lockByUserId(long userId) {
        return accounts.selectByUserIdForUpdate(userId);
    }

    @Override
    public int applyDelta(long userId, long delta) {
        return accounts.applyDelta(userId, delta);
    }

    @Override
    public int insertTx(PntTransactionEntity row) {
        return transactions.insert(row);
    }

    @Override
    public boolean expireExists(String bizId) {
        return transactions.countExpireByBizId(bizId) > 0;
    }

    @Override
    public List<PntTransactionEntity> listExpireDue(LocalDateTime now, int limit) {
        return transactions.listExpireDue(now, limit);
    }

    @Override
    public long countAccounts(Long userId) {
        return accounts.selectCountFiltered(userId);
    }

    @Override
    public List<PntAccountEntity> listAccounts(Long userId, long offset, int limit) {
        return accounts.selectPageFiltered(userId, offset, limit);
    }

    @Override
    public long countTx(Long userId, String type, LocalDateTime from, LocalDateTime to) {
        return transactions.selectCountFiltered(userId, type, from, to);
    }

    @Override
    public List<PntTransactionEntity> listTx(
            Long userId, String type, LocalDateTime from, LocalDateTime to, long offset, int limit) {
        return transactions.selectPageFiltered(userId, type, from, to, offset, limit);
    }
}
