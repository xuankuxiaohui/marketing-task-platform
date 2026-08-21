package com.mkt.activity.testsupport;

import com.mkt.activity.application.ActivityActivityStore;
import com.mkt.activity.application.ActivityParticipationStore;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.HitRuleCountView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryActivityStores {

    public final MemoryActivities activities = new MemoryActivities();
    public final MemoryParticipations participations = new MemoryParticipations();

    public static final class MemoryActivities implements ActivityActivityStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, ActActivityEntity> byId = new ConcurrentHashMap<>();

        @Override
        public ActActivityEntity getById(long id) {
            return copy(byId.get(id));
        }

        @Override
        public ActActivityEntity getByCode(String code) {
            return byId.values().stream()
                    .filter(row -> code.equals(row.getCode()) && (row.getDeleted() == null || row.getDeleted() == 0))
                    .findFirst()
                    .map(MemoryActivities::copy)
                    .orElse(null);
        }

        @Override
        public int insert(ActActivityEntity entity) {
            if (getByCode(entity.getCode()) != null) {
                throw new DuplicateKeyException("uk_code");
            }
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int update(ActActivityEntity entity) {
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public long countByQuery(String code, String name, String status) {
            return listByQuery(code, name, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<ActActivityEntity> listByQuery(String code, String name, String status, long offset, int limit) {
            return byId.values().stream()
                    .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                    .filter(row -> code == null || code.isBlank() || code.equals(row.getCode()))
                    .filter(row -> name == null
                            || name.isBlank()
                            || (row.getName() != null && row.getName().contains(name)))
                    .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                    .sorted(Comparator.comparing(ActActivityEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(MemoryActivities::copy)
                    .toList();
        }

        @Override
        public ActActivityEntity getByIdForUpdate(long id) {
            return getById(id);
        }

        @Override
        public List<ActActivityEntity> listPublished() {
            return listByQuery(null, null, "PUBLISHED", 0, Integer.MAX_VALUE);
        }

        @Override
        public List<ActActivityEntity> listDueScheduled(LocalDateTime now, int limit) {
            return byId.values().stream()
                    .filter(row -> "SCHEDULED".equals(row.getStatus()))
                    .filter(row -> row.getSchedulePublishAt() != null && !row.getSchedulePublishAt().isAfter(now))
                    .limit(limit)
                    .map(MemoryActivities::copy)
                    .toList();
        }

        @Override
        public List<ActActivityEntity> listDueOffline(LocalDateTime now, int limit) {
            return byId.values().stream()
                    .filter(row -> "PUBLISHED".equals(row.getStatus()))
                    .filter(row -> row.getScheduleOfflineAt() != null && !row.getScheduleOfflineAt().isAfter(now))
                    .limit(limit)
                    .map(MemoryActivities::copy)
                    .toList();
        }

        private static ActActivityEntity copy(ActActivityEntity src) {
            if (src == null) {
                return null;
            }
            ActActivityEntity c = new ActActivityEntity();
            c.setId(src.getId());
            c.setCode(src.getCode());
            c.setName(src.getName());
            c.setRichText(src.getRichText());
            c.setContentHash(src.getContentHash());
            c.setStartTime(src.getStartTime());
            c.setEndTime(src.getEndTime());
            c.setStatus(src.getStatus());
            c.setVersion(src.getVersion());
            c.setSchedulePublishAt(src.getSchedulePublishAt());
            c.setScheduleOfflineAt(src.getScheduleOfflineAt());
            c.setGrayType(src.getGrayType());
            c.setGrayRatio(src.getGrayRatio());
            c.setSubmodules(src.getSubmodules());
            c.setParticipationPrizeId(src.getParticipationPrizeId());
            c.setAllowUserIds(src.getAllowUserIds());
            c.setAllowCrowdCodes(src.getAllowCrowdCodes());
            c.setNewUserOnly(src.getNewUserOnly());
            c.setNewUserDays(src.getNewUserDays());
            c.setUserDailyLimit(src.getUserDailyLimit());
            c.setUserTotalLimit(src.getUserTotalLimit());
            c.setGlobalDailyLimit(src.getGlobalDailyLimit());
            c.setRegions(src.getRegions());
            c.setQuotaDay(src.getQuotaDay());
            c.setQuotaCount(src.getQuotaCount());
            c.setDraftContent(src.getDraftContent());
            c.setPendingRevision(src.getPendingRevision());
            c.setDeleted(src.getDeleted());
            c.setCreatedBy(src.getCreatedBy());
            c.setCreatedAt(src.getCreatedAt());
            c.setUpdatedAt(src.getUpdatedAt());
            return c;
        }
    }

    public static final class MemoryParticipations implements ActivityParticipationStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, ActParticipationEntity> byId = new ConcurrentHashMap<>();

        @Override
        public int insert(ActParticipationEntity entity) {
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int countPass(long activityId, Long userId, String periodKey) {
            return (int) byId.values().stream()
                    .filter(row -> Objects.equals(row.getActivityId(), activityId))
                    .filter(row -> "PASS".equals(row.getResult()))
                    .filter(row -> userId == null || Objects.equals(row.getUserId(), userId))
                    .filter(row -> periodKey == null || periodKey.isBlank() || periodKey.equals(row.getPeriodKey()))
                    .count();
        }

        @Override
        public long countByQuery(Long activityId, Long userId, String result, String periodKey) {
            return listByQuery(activityId, userId, result, periodKey, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<ActParticipationEntity> listByQuery(
                Long activityId, Long userId, String result, String periodKey, long offset, int limit) {
            return byId.values().stream()
                    .filter(row -> activityId == null || Objects.equals(row.getActivityId(), activityId))
                    .filter(row -> userId == null || Objects.equals(row.getUserId(), userId))
                    .filter(row -> result == null || result.isBlank() || result.equals(row.getResult()))
                    .filter(row -> periodKey == null || periodKey.isBlank() || periodKey.equals(row.getPeriodKey()))
                    .sorted(Comparator.comparing(ActParticipationEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(MemoryParticipations::copy)
                    .toList();
        }

        @Override
        public long countAll(long activityId) {
            return byId.values().stream().filter(row -> Objects.equals(row.getActivityId(), activityId)).count();
        }

        @Override
        public long countPassByActivity(long activityId) {
            return countPass(activityId, null, null);
        }

        @Override
        public List<HitRuleCountView> countRejectByRule(long activityId) {
            java.util.Map<String, Long> counts = new java.util.TreeMap<>();
            for (ActParticipationEntity row : byId.values()) {
                if (Objects.equals(row.getActivityId(), activityId)
                        && "REJECT".equals(row.getResult())
                        && row.getHitRule() != null) {
                    counts.merge(row.getHitRule(), 1L, Long::sum);
                }
            }
            List<HitRuleCountView> out = new ArrayList<>();
            counts.forEach((rule, n) -> out.add(new HitRuleCountView(rule, n)));
            out.sort(Comparator.comparingLong(HitRuleCountView::count).reversed().thenComparing(HitRuleCountView::hitRule));
            return out;
        }

        private static ActParticipationEntity copy(ActParticipationEntity src) {
            ActParticipationEntity c = new ActParticipationEntity();
            c.setId(src.getId());
            c.setActivityId(src.getActivityId());
            c.setUserId(src.getUserId());
            c.setPeriodKey(src.getPeriodKey());
            c.setResult(src.getResult());
            c.setHitRule(src.getHitRule());
            c.setSimulated(src.getSimulated());
            c.setCreatedAt(src.getCreatedAt());
            return c;
        }
    }
}
