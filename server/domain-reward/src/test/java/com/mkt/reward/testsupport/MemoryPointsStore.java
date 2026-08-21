package com.mkt.reward.testsupport;

import com.mkt.reward.application.PointsStore;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryPointsStore implements PointsStore {

    private final ConcurrentHashMap<Long, PntAccountEntity> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PntTransactionEntity> txs = new ConcurrentHashMap<>();
    private final AtomicLong accountSeq = new AtomicLong(1);
    private final AtomicLong txSeq = new AtomicLong(1);

    @Override
    public int insertIgnoreAccount(long userId) {
        if (accounts.containsKey(userId)) {
            return 0;
        }
        PntAccountEntity row = new PntAccountEntity();
        row.setId(accountSeq.getAndIncrement());
        row.setUserId(userId);
        row.setBalance(0L);
        PntAccountEntity previous = accounts.putIfAbsent(userId, row);
        return previous == null ? 1 : 0;
    }

    @Override
    public PntAccountEntity getByUserId(long userId) {
        return copyAccount(accounts.get(userId));
    }

    @Override
    public PntAccountEntity lockByUserId(long userId) {
        return getByUserId(userId);
    }

    @Override
    public int applyDelta(long userId, long delta) {
        PntAccountEntity row = accounts.get(userId);
        if (row == null) {
            return 0;
        }
        synchronized (row) {
            long current = row.getBalance() == null ? 0L : row.getBalance();
            if (current + delta < 0) {
                return 0;
            }
            row.setBalance(current + delta);
            return 1;
        }
    }

    @Override
    public int insertTx(PntTransactionEntity row) {
        if (row.getId() == null) {
            row.setId(txSeq.getAndIncrement());
        }
        txs.put(row.getId(), copyTx(row));
        return 1;
    }

    @Override
    public boolean expireExists(String bizId) {
        for (PntTransactionEntity row : txs.values()) {
            if (PointTypes.EXPIRE.equals(row.getType()) && bizId.equals(row.getBizId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<PntTransactionEntity> listExpireDue(LocalDateTime now, int limit) {
        List<PntTransactionEntity> due = new ArrayList<>();
        for (PntTransactionEntity row : txs.values()) {
            if (!PointTypes.EARN.equals(row.getType()) || row.getExpireAt() == null) {
                continue;
            }
            if (row.getExpireAt().isAfter(now)) {
                continue;
            }
            if (expireExists(String.valueOf(row.getId()))) {
                continue;
            }
            due.add(copyTx(row));
        }
        due.sort(Comparator.comparing(PntTransactionEntity::getExpireAt)
                .thenComparing(PntTransactionEntity::getId));
        if (due.size() > limit) {
            return new ArrayList<>(due.subList(0, limit));
        }
        return due;
    }

    @Override
    public long countAccounts(Long userId) {
        return listAccounts(userId, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public List<PntAccountEntity> listAccounts(Long userId, long offset, int limit) {
        List<PntAccountEntity> rows = new ArrayList<>();
        for (PntAccountEntity row : accounts.values()) {
            if (userId != null && !userId.equals(row.getUserId())) {
                continue;
            }
            rows.add(copyAccount(row));
        }
        rows.sort(Comparator.comparing(PntAccountEntity::getId).reversed());
        return slice(rows, offset, limit);
    }

    @Override
    public long countTx(Long userId, String type, LocalDateTime from, LocalDateTime to) {
        return filterTx(userId, type, from, to).size();
    }

    @Override
    public List<PntTransactionEntity> listTx(
            Long userId, String type, LocalDateTime from, LocalDateTime to, long offset, int limit) {
        List<PntTransactionEntity> rows = filterTx(userId, type, from, to);
        rows.sort(Comparator.comparing(PntTransactionEntity::getCreatedAt)
                .thenComparing(PntTransactionEntity::getId)
                .reversed());
        return slice(rows, offset, limit);
    }

    public List<PntTransactionEntity> listChronological(long userId) {
        List<PntTransactionEntity> rows = filterTx(userId, null, null, null);
        rows.sort(Comparator.comparing(PntTransactionEntity::getId));
        return rows;
    }

    private List<PntTransactionEntity> filterTx(Long userId, String type, LocalDateTime from, LocalDateTime to) {
        List<PntTransactionEntity> rows = new ArrayList<>();
        for (PntTransactionEntity row : txs.values()) {
            if (userId != null && !userId.equals(row.getUserId())) {
                continue;
            }
            if (type != null && !type.equals(row.getType())) {
                continue;
            }
            if (from != null && (row.getCreatedAt() == null || row.getCreatedAt().isBefore(from))) {
                continue;
            }
            if (to != null && (row.getCreatedAt() == null || !row.getCreatedAt().isBefore(to))) {
                continue;
            }
            rows.add(copyTx(row));
        }
        return rows;
    }

    private static <T> List<T> slice(List<T> rows, long offset, int limit) {
        int from = offset > Integer.MAX_VALUE ? rows.size() : (int) offset;
        if (from >= rows.size()) {
            return List.of();
        }
        int to = Math.min(rows.size(), from + limit);
        return new ArrayList<>(rows.subList(from, to));
    }

    private static PntAccountEntity copyAccount(PntAccountEntity src) {
        if (src == null) {
            return null;
        }
        PntAccountEntity copy = new PntAccountEntity();
        copy.setId(src.getId());
        copy.setUserId(src.getUserId());
        copy.setBalance(src.getBalance());
        copy.setCreatedAt(src.getCreatedAt());
        copy.setUpdatedAt(src.getUpdatedAt());
        return copy;
    }

    private static PntTransactionEntity copyTx(PntTransactionEntity src) {
        PntTransactionEntity copy = new PntTransactionEntity();
        copy.setId(src.getId());
        copy.setUserId(src.getUserId());
        copy.setType(src.getType());
        copy.setAmount(src.getAmount());
        copy.setBalanceAfter(src.getBalanceAfter());
        copy.setBizSource(src.getBizSource());
        copy.setBizId(src.getBizId());
        copy.setExpireAt(src.getExpireAt());
        copy.setRemark(src.getRemark());
        copy.setSimulated(src.getSimulated());
        copy.setCreatedAt(src.getCreatedAt());
        return copy;
    }
}
