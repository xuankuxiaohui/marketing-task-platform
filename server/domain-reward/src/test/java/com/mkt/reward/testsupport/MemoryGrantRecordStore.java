package com.mkt.reward.testsupport;

import com.mkt.reward.application.GrantRecordStore;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.SpendRowView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryGrantRecordStore implements GrantRecordStore {

    private final ConcurrentHashMap<Long, GrantRecordEntity> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> persistedStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> persistedFulfillment = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public GrantRecordEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public GrantRecordEntity getByIdempotent(String grantSource, String sourceId, long prizeId) {
        Long id = keys.get(key(grantSource, sourceId, prizeId));
        return id == null ? null : rows.get(id);
    }

    @Override
    public int insert(GrantRecordEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        String uk = key(entity.getGrantSource(), entity.getSourceId(), entity.getPrizeId());
        Long previous = keys.putIfAbsent(uk, entity.getId());
        if (previous != null) {
            throw new DuplicateKeyException("uk_idempotent");
        }
        rows.put(entity.getId(), entity);
        persistedStatus.put(entity.getId(), entity.getStatus());
        persistedFulfillment.put(entity.getId(), entity.getFulfillmentStatus());
        return 1;
    }

    @Override
    public int update(GrantRecordEntity entity) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        persistedStatus.put(entity.getId(), entity.getStatus());
        persistedFulfillment.put(entity.getId(), entity.getFulfillmentStatus());
        return 1;
    }

    @Override
    public int updateIfStatus(GrantRecordEntity entity, String expectedStatus) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        boolean replaced = persistedStatus.replace(entity.getId(), expectedStatus, entity.getStatus());
        if (!replaced) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        persistedFulfillment.put(entity.getId(), entity.getFulfillmentStatus());
        return 1;
    }

    @Override
    public int updateFulfillmentRef(long id, String fulfillmentRef) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null || entity.getFulfillmentRef() != null) {
            return 0;
        }
        entity.setFulfillmentRef(fulfillmentRef);
        return 1;
    }

    @Override
    public long countActive(long userId, long prizeId, LocalDateTime from) {
        return rows.values().stream()
                .filter(row -> userId == row.getUserId()
                        && prizeId == row.getPrizeId()
                        && GrantRecordStatuses.LIMIT_COUNT.contains(row.getStatus())
                        && (from == null
                                || row.getCreatedAt() == null
                                || !row.getCreatedAt().isBefore(from)))
                .count();
    }

    @Override
    public int markPermanentFailed(long id) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        entity.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        return 1;
    }

    @Override
    public List<GrantRecordEntity> listDueRetry(LocalDateTime now, int limit) {
        List<GrantRecordEntity> due = new ArrayList<>();
        for (GrantRecordEntity row : rows.values()) {
            if (!GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())) {
                continue;
            }
            if (row.getNextRetryAt() == null || row.getNextRetryAt().isAfter(now)) {
                continue;
            }
            if (row.getExpireAt() != null && !row.getExpireAt().isAfter(now)) {
                continue;
            }
            due.add(row);
        }
        due.sort(Comparator.comparing(GrantRecordEntity::getNextRetryAt).thenComparing(GrantRecordEntity::getId));
        return due.subList(0, Math.min(limit, due.size()));
    }

    public List<GrantRecordEntity> all() {
        return List.copyOf(rows.values());
    }

    @Override
    public int casClaimStart(long id, LocalDateTime now, int retryMax) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        String status = persistedStatus.get(id);
        if (!GrantRecordStatuses.WON.equals(status) && !GrantRecordStatuses.RETRY_PENDING.equals(status)) {
            return 0;
        }
        if (entity.getExpireAt() != null && !entity.getExpireAt().isAfter(now)) {
            return 0;
        }
        int retry = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        if (GrantRecordStatuses.RETRY_PENDING.equals(status) && retry >= retryMax) {
            return 0;
        }
        boolean replaced = persistedStatus.replace(id, status, GrantRecordStatuses.CLAIMING);
        if (!replaced) {
            return 0;
        }
        if (GrantRecordStatuses.RETRY_PENDING.equals(status)) {
            entity.setRetryCount(retry + 1);
        }
        entity.setStatus(GrantRecordStatuses.CLAIMING);
        entity.setUpdatedAt(now);
        return 1;
    }

    @Override
    public int casExpireOne(long id, LocalDateTime now) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null || entity.getExpireAt() == null || entity.getExpireAt().isAfter(now)) {
            return 0;
        }
        String status = persistedStatus.get(id);
        if (!GrantRecordStatuses.WON.equals(status)
                && !GrantRecordStatuses.RETRY_PENDING.equals(status)
                && !GrantRecordStatuses.CLAIMING.equals(status)) {
            return 0;
        }
        if (!persistedStatus.replace(id, status, GrantRecordStatuses.EXPIRED)) {
            return 0;
        }
        entity.setStatus(GrantRecordStatuses.EXPIRED);
        entity.setUpdatedAt(now);
        return 1;
    }

    @Override
    public int casPermanentFromRetry(long id, LocalDateTime now, int retryMax) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        int retry = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        if (retry < retryMax) {
            return 0;
        }
        if (!persistedStatus.replace(id, GrantRecordStatuses.RETRY_PENDING, GrantRecordStatuses.PERMANENT_FAILED)) {
            return 0;
        }
        entity.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        entity.setUpdatedAt(now);
        return 1;
    }

    @Override
    public GrantRecordEntity getByFulfillmentRef(String fulfillmentRef) {
        if (fulfillmentRef == null) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> fulfillmentRef.equals(row.getFulfillmentRef()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int updateIfFulfillment(GrantRecordEntity entity, String expectedFulfillment) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        boolean replaced =
                persistedFulfillment.replace(entity.getId(), expectedFulfillment, entity.getFulfillmentStatus());
        if (!replaced) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        persistedStatus.put(entity.getId(), entity.getStatus());
        return 1;
    }

    @Override
    public int closeAsManual(long id, LocalDateTime now) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        String fulfill = entity.getFulfillmentStatus();
        if (!GrantRecordStatuses.FULFILL_SENDING.equals(fulfill)
                && !GrantRecordStatuses.FULFILL_FAILED.equals(fulfill)) {
            return 0;
        }
        entity.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
        entity.setFulfillFailReason(FulfillFailReasons.MANUAL);
        entity.setUpdatedAt(now);
        persistedFulfillment.put(id, GrantRecordStatuses.FULFILL_FAILED);
        return 1;
    }

    @Override
    public List<GrantRecordEntity> listClaimingTimeout(LocalDateTime cutoff, int limit) {
        return rows.values().stream()
                .filter(row -> GrantRecordStatuses.CLAIMING.equals(row.getStatus())
                        && row.getUpdatedAt() != null
                        && row.getUpdatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(GrantRecordEntity::getUpdatedAt).thenComparing(GrantRecordEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public int rollbackClaiming(long id, LocalDateTime now) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        if (!persistedStatus.replace(id, GrantRecordStatuses.CLAIMING, GrantRecordStatuses.WON)) {
            return 0;
        }
        int retry = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        entity.setRetryCount(retry + 1);
        entity.setStatus(GrantRecordStatuses.WON);
        entity.setUpdatedAt(now);
        return 1;
    }

    @Override
    public List<GrantRecordEntity> listPrizeExpireDue(LocalDateTime now, int limit) {
        return rows.values().stream()
                .filter(row -> (GrantRecordStatuses.WON.equals(row.getStatus())
                                || GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus()))
                        && row.getExpireAt() != null
                        && !row.getExpireAt().isAfter(now))
                .sorted(Comparator.comparing(GrantRecordEntity::getExpireAt).thenComparing(GrantRecordEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public List<GrantRecordEntity> listFulfillRetryDue(LocalDateTime now, int limit) {
        return rows.values().stream()
                .filter(row -> GrantRecordStatuses.GRANTED.equals(row.getStatus())
                        && GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())
                        && row.getNextFulfillRetryAt() != null
                        && !row.getNextFulfillRetryAt().isAfter(now))
                .sorted(Comparator.comparing(GrantRecordEntity::getNextFulfillRetryAt)
                        .thenComparing(GrantRecordEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public List<GrantRecordEntity> listSendingTimeout(LocalDateTime cutoff, int limit) {
        return rows.values().stream()
                .filter(row -> GrantRecordStatuses.GRANTED.equals(row.getStatus())
                        && GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())
                        && row.getUpdatedAt() != null
                        && row.getUpdatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(GrantRecordEntity::getUpdatedAt).thenComparing(GrantRecordEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public List<GrantRecordEntity> listCrossDaySending(LocalDateTime dayStart, int limit) {
        return rows.values().stream()
                .filter(row -> GrantRecordStatuses.GRANTED.equals(row.getStatus())
                        && GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())
                        && GrantRecordStatuses.RECON_NONE.equals(row.getReconStatus())
                        && row.getGrantedAt() != null
                        && row.getGrantedAt().isBefore(dayStart))
                .sorted(Comparator.comparing(GrantRecordEntity::getGrantedAt).thenComparing(GrantRecordEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public int markReconPending(long id, LocalDateTime now) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null || !GrantRecordStatuses.RECON_NONE.equals(entity.getReconStatus())) {
            return 0;
        }
        entity.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        entity.setUpdatedAt(now);
        return 1;
    }

    @Override
    public List<GrantRecordEntity> listPlatformRecon(String categoryCode, LocalDateTime from, LocalDateTime to) {
        return rows.values().stream()
                .filter(row -> categoryCode.equals(row.getCategoryCode())
                        && GrantRecordStatuses.RECON_PENDING.equals(row.getReconStatus())
                        && (row.getSimulated() == null || row.getSimulated() == 0)
                        && (GrantRecordStatuses.FULFILL_ARRIVED.equals(row.getFulfillmentStatus())
                                || GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())
                                || GrantRecordStatuses.FULFILL_FAILED.equals(row.getFulfillmentStatus()))
                        && row.getGrantedAt() != null
                        && !row.getGrantedAt().isBefore(from)
                        && row.getGrantedAt().isBefore(to))
                .sorted(Comparator.comparing(GrantRecordEntity::getId))
                .toList();
    }

    @Override
    public List<GrantRecordEntity> listPortalPrizes(long userId, boolean pendingOnly, long offset, int limit) {
        List<GrantRecordEntity> filtered = rows.values().stream()
                .filter(row -> userId == row.getUserId())
                .filter(row -> !pendingOnly || pending(row))
                .sorted(Comparator.comparing(GrantRecordEntity::getId).reversed())
                .toList();
        int from = (int) Math.min(offset, filtered.size());
        int to = (int) Math.min(offset + limit, filtered.size());
        return filtered.subList(from, to);
    }

    @Override
    public long countPortalPrizes(long userId, boolean pendingOnly) {
        return rows.values().stream()
                .filter(row -> userId == row.getUserId())
                .filter(row -> !pendingOnly || pending(row))
                .count();
    }

    @Override
    public List<SpendRowView> sumSpend(String categoryCode, Long prizeId, LocalDateTime from, LocalDateTime to) {
        Map<String, long[]> acc = new HashMap<>();
        for (GrantRecordEntity row : rows.values()) {
            if (row.getSimulated() != null && row.getSimulated() == 1) {
                continue;
            }
            if (categoryCode != null && !categoryCode.isBlank() && !categoryCode.equals(row.getCategoryCode())) {
                continue;
            }
            if (prizeId != null && !prizeId.equals(row.getPrizeId())) {
                continue;
            }
            if (from != null && (row.getGrantedAt() == null || row.getGrantedAt().isBefore(from))) {
                continue;
            }
            if (to != null && (row.getGrantedAt() == null || !row.getGrantedAt().isBefore(to))) {
                continue;
            }
            long[] nums = acc.computeIfAbsent(row.getCategoryCode(), key -> new long[4]);
            int cost = row.getCostFen() == null ? 0 : row.getCostFen();
            if (GrantRecordStatuses.FULFILL_ARRIVED.equals(row.getFulfillmentStatus())) {
                nums[0]++;
                nums[1] += cost;
            } else if (GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())) {
                nums[2]++;
                nums[3] += cost;
            }
        }
        List<SpendRowView> views = new ArrayList<>();
        acc.keySet().stream().sorted().forEach(code -> {
            long[] nums = acc.get(code);
            views.add(new SpendRowView(code, nums[0], nums[1], nums[2], nums[3]));
        });
        return views;
    }

    private static boolean pending(GrantRecordEntity row) {
        return GrantRecordStatuses.WON.equals(row.getStatus())
                || GrantRecordStatuses.CLAIMING.equals(row.getStatus())
                || GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())
                || (GrantRecordStatuses.GRANTED.equals(row.getStatus())
                        && (GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())
                                || GrantRecordStatuses.FULFILL_FAILED.equals(row.getFulfillmentStatus())));
    }

    private static String key(String grantSource, String sourceId, Long prizeId) {
        return grantSource + '\0' + sourceId + '\0' + prizeId;
    }
}
