package com.marketing.task.signin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.signin.domain.entity.PointAccount;
import com.marketing.task.signin.domain.entity.PointTransaction;
import com.marketing.task.signin.domain.enums.PointTransactionStatus;
import com.marketing.task.signin.domain.enums.PointTransactionType;
import com.marketing.task.signin.mapper.PointAccountMapper;
import com.marketing.task.signin.mapper.PointTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {
    private final PointAccountMapper accountMapper;
    private final PointTransactionMapper transactionMapper;

    @Transactional
    public void earn(String userId, long amount, String sourceType, Long sourceId,
                     LocalDateTime expireAt, String description) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分数量必须大于0");
        }
        PointAccount account = getOrCreateAccount(userId);
        account.setBalance(account.getBalance() + amount);
        account.setTotalEarned(account.getTotalEarned() + amount);
        accountMapper.updateById(account);

        PointTransaction tx = new PointTransaction();
        tx.setUserId(userId);
        tx.setType(PointTransactionType.EARN.name());
        tx.setAmount(amount);
        tx.setSourceType(sourceType);
        tx.setSourceId(sourceId);
        tx.setBalanceAfter(account.getBalance());
        tx.setExpireAt(expireAt);
        tx.setStatus(PointTransactionStatus.ACTIVE.name());
        tx.setDescription(description);
        transactionMapper.insert(tx);

        log.info("积分入账: userId={}, amount={}, source={}#{}, balance={}",
                userId, amount, sourceType, sourceId, account.getBalance());
    }

    @Transactional
    public void deduct(String userId, long amount, String sourceType, Long sourceId,
                       String description) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "扣减数量必须大于0");
        }
        PointAccount account = getOrCreateAccount(userId);
        if (account.getBalance() < amount) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分余额不足");
        }
        account.setBalance(account.getBalance() - amount);
        account.setTotalSpent(account.getTotalSpent() + amount);
        accountMapper.updateById(account);

        PointTransaction tx = new PointTransaction();
        tx.setUserId(userId);
        tx.setType(PointTransactionType.DEDUCT.name());
        tx.setAmount(amount);
        tx.setSourceType(sourceType);
        tx.setSourceId(sourceId);
        tx.setBalanceAfter(account.getBalance());
        tx.setStatus(PointTransactionStatus.ACTIVE.name());
        tx.setDescription(description);
        transactionMapper.insert(tx);

        log.info("积分扣减: userId={}, amount={}, source={}#{}, balance={}",
                userId, amount, sourceType, sourceId, account.getBalance());
    }

    @Transactional
    public void expire(Long transactionId) {
        PointTransaction original = transactionMapper.selectById(transactionId);
        if (original == null || !PointTransactionStatus.ACTIVE.name().equals(original.getStatus())) {
            return;
        }

        PointAccount account = getOrCreateAccount(original.getUserId());
        long expireAmount = Math.min(original.getAmount(), account.getBalance());
        if (expireAmount <= 0) {
            original.setStatus(PointTransactionStatus.EXPIRED.name());
            transactionMapper.updateById(original);
            return;
        }

        account.setBalance(account.getBalance() - expireAmount);
        account.setTotalExpired(account.getTotalExpired() + expireAmount);
        accountMapper.updateById(account);

        original.setStatus(PointTransactionStatus.EXPIRED.name());
        transactionMapper.updateById(original);

        PointTransaction expireTx = new PointTransaction();
        expireTx.setUserId(original.getUserId());
        expireTx.setType(PointTransactionType.EXPIRE.name());
        expireTx.setAmount(expireAmount);
        expireTx.setSourceType(original.getSourceType());
        expireTx.setSourceId(original.getId());
        expireTx.setBalanceAfter(account.getBalance());
        expireTx.setStatus(PointTransactionStatus.ACTIVE.name());
        expireTx.setDescription("积分过期");
        transactionMapper.insert(expireTx);

        log.info("积分过期: userId={}, amount={}, originalTxId={}, balance={}",
                original.getUserId(), expireAmount, transactionId, account.getBalance());
    }

    public PointAccount getBalance(String userId) {
        return getOrCreateAccount(userId);
    }

    public Page<PointTransaction> getTransactions(String userId, String type, int page, int size) {
        Page<PointTransaction> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PointTransaction> wrapper = new LambdaQueryWrapper<PointTransaction>()
                .eq(PointTransaction::getUserId, userId)
                .orderByDesc(PointTransaction::getCreatedAt);
        if (type != null && !type.isBlank()) {
            wrapper.eq(PointTransaction::getType, type);
        }
        return transactionMapper.selectPage(pageParam, wrapper);
    }

    private PointAccount getOrCreateAccount(String userId) {
        PointAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>()
                        .eq(PointAccount::getUserId, userId));
        if (account == null) {
            account = new PointAccount();
            account.setUserId(userId);
            account.setBalance(0L);
            account.setTotalEarned(0L);
            account.setTotalSpent(0L);
            account.setTotalExpired(0L);
            accountMapper.insert(account);
        }
        return account;
    }
}
