package com.mkt.signin.testsupport;

import com.mkt.signin.application.SigninActivityStore;
import com.mkt.signin.application.SigninRecordStore;
import com.mkt.signin.application.SigninSnapshotStore;
import com.mkt.signin.entity.SgnActivityEntity;
import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import com.mkt.signin.entity.SgnRecordEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemorySigninStores {

    public final MemoryActivities activities = new MemoryActivities();
    public final MemorySnapshots snapshots = new MemorySnapshots();
    public final MemoryRecords records = new MemoryRecords();

    public static final class MemoryActivities implements SigninActivityStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, SgnActivityEntity> byId = new ConcurrentHashMap<>();

        @Override
        public SgnActivityEntity getById(long id) {
            return copy(byId.get(id));
        }

        @Override
        public SgnActivityEntity getByCode(String code) {
            return byId.values().stream()
                    .filter(row -> code.equals(row.getCode()) && (row.getDeleted() == null || row.getDeleted() == 0))
                    .findFirst()
                    .map(MemoryActivities::copy)
                    .orElse(null);
        }

        @Override
        public int insert(SgnActivityEntity entity) {
            if (getByCode(entity.getCode()) != null) {
                throw new DuplicateKeyException("uk_code");
            }
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int update(SgnActivityEntity entity) {
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public long countByQuery(String code, String name, String status) {
            return listByQuery(code, name, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<SgnActivityEntity> listByQuery(String code, String name, String status, long offset, int limit) {
            return byId.values().stream()
                    .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                    .filter(row -> code == null || code.isBlank() || code.equals(row.getCode()))
                    .filter(row -> name == null || name.isBlank() || (row.getName() != null && row.getName().contains(name)))
                    .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                    .sorted(Comparator.comparing(SgnActivityEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(MemoryActivities::copy)
                    .toList();
        }

        @Override
        public SgnActivityEntity getByIdForUpdate(long id) {
            return getById(id);
        }

        @Override
        public List<SgnActivityEntity> listPublished() {
            return listByQuery(null, null, "PUBLISHED", 0, Integer.MAX_VALUE);
        }

        @Override
        public List<SgnActivityEntity> listDueScheduled(LocalDateTime now, int limit) {
            return byId.values().stream()
                    .filter(row -> "SCHEDULED".equals(row.getStatus()))
                    .filter(row -> row.getSchedulePublishAt() != null && !row.getSchedulePublishAt().isAfter(now))
                    .limit(limit)
                    .map(MemoryActivities::copy)
                    .toList();
        }

        @Override
        public int casPublish(
                long id,
                String expectedStatus,
                int expectedVersion,
                int expectedPending,
                int nextVersion,
                LocalDateTime updatedAt) {
            SgnActivityEntity current = byId.get(id);
            if (current == null) {
                return 0;
            }
            int pending = current.getPendingRevision() == null ? 0 : current.getPendingRevision();
            int version = current.getVersion() == null ? 0 : current.getVersion();
            if (!expectedStatus.equals(current.getStatus())
                    || version != expectedVersion
                    || pending != expectedPending) {
                return 0;
            }
            current.setStatus("PUBLISHED");
            current.setVersion(nextVersion);
            current.setPendingRevision(0);
            current.setSchedulePublishAt(null);
            current.setUpdatedAt(updatedAt);
            return 1;
        }

        private static SgnActivityEntity copy(SgnActivityEntity src) {
            if (src == null) {
                return null;
            }
            SgnActivityEntity copy = new SgnActivityEntity();
            copy.setId(src.getId());
            copy.setCode(src.getCode());
            copy.setName(src.getName());
            copy.setStartTime(src.getStartTime());
            copy.setEndTime(src.getEndTime());
            copy.setStatus(src.getStatus());
            copy.setVersion(src.getVersion());
            copy.setSchedulePublishAt(src.getSchedulePublishAt());
            copy.setDraftContent(src.getDraftContent());
            copy.setPendingRevision(src.getPendingRevision());
            copy.setDeleted(src.getDeleted());
            copy.setCreatedBy(src.getCreatedBy());
            copy.setCreatedAt(src.getCreatedAt());
            copy.setUpdatedAt(src.getUpdatedAt());
            return copy;
        }
    }

    public static final class MemorySnapshots implements SigninSnapshotStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, SgnActivitySnapshotEntity> byId = new ConcurrentHashMap<>();

        @Override
        public int insert(SgnActivitySnapshotEntity entity) {
            String key = entity.getActivityId() + ":" + entity.getVersion();
            boolean dup = byId.values().stream()
                    .anyMatch(row -> key.equals(row.getActivityId() + ":" + row.getVersion()));
            if (dup) {
                throw new DuplicateKeyException("uk_activity_version");
            }
            entity.setId(seq.getAndIncrement());
            SgnActivitySnapshotEntity copy = new SgnActivitySnapshotEntity();
            copy.setId(entity.getId());
            copy.setActivityId(entity.getActivityId());
            copy.setVersion(entity.getVersion());
            copy.setContent(entity.getContent());
            copy.setPublishedAt(entity.getPublishedAt());
            copy.setPublishedBy(entity.getPublishedBy());
            byId.put(copy.getId(), copy);
            return 1;
        }

        @Override
        public SgnActivitySnapshotEntity getById(long id) {
            return byId.get(id);
        }

        @Override
        public SgnActivitySnapshotEntity getByActivityAndVersion(long activityId, int version) {
            return byId.values().stream()
                    .filter(row -> Objects.equals(row.getActivityId(), activityId) && version == row.getVersion())
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<SgnActivitySnapshotEntity> listByActivityId(long activityId) {
            return byId.values().stream()
                    .filter(row -> Objects.equals(row.getActivityId(), activityId))
                    .sorted(Comparator.comparing(SgnActivitySnapshotEntity::getVersion).reversed())
                    .toList();
        }
    }

    public static final class MemoryRecords implements SigninRecordStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<String, SgnRecordEntity> byUk = new ConcurrentHashMap<>();

        private static String key(long activityId, long userId, LocalDate date) {
            return activityId + ":" + userId + ":" + date;
        }

        @Override
        public int insert(SgnRecordEntity entity) {
            String uk = key(entity.getActivityId(), entity.getUserId(), entity.getSignDate());
            if (byUk.putIfAbsent(uk, entity) != null) {
                throw new DuplicateKeyException("uk_activity_user_date");
            }
            entity.setId(seq.getAndIncrement());
            return 1;
        }

        @Override
        public SgnRecordEntity getByUk(long activityId, long userId, LocalDate signDate) {
            return byUk.get(key(activityId, userId, signDate));
        }

        @Override
        public List<LocalDate> listSignDates(long activityId, long userId) {
            return byUk.values().stream()
                    .filter(row -> row.getActivityId() == activityId && row.getUserId() == userId)
                    .map(SgnRecordEntity::getSignDate)
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }

        @Override
        public List<SgnRecordEntity> listInRange(long activityId, long userId, LocalDate from, LocalDate to) {
            return byUk.values().stream()
                    .filter(row -> row.getActivityId() == activityId && row.getUserId() == userId)
                    .filter(row -> !row.getSignDate().isBefore(from) && !row.getSignDate().isAfter(to))
                    .toList();
        }

        @Override
        public int countCatchupBetween(long activityId, long userId, LocalDateTime from, LocalDateTime to) {
            return (int) byUk.values().stream()
                    .filter(row -> row.getActivityId() == activityId && row.getUserId() == userId)
                    .filter(row -> "CATCHUP".equals(row.getSource()))
                    .filter(row -> row.getCreatedAt() != null
                            && !row.getCreatedAt().isBefore(from)
                            && row.getCreatedAt().isBefore(to))
                    .count();
        }

        @Override
        public long countByQuery(Long activityId, Long userId, LocalDate from, LocalDate to) {
            return listByQuery(activityId, userId, from, to, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<SgnRecordEntity> listByQuery(
                Long activityId, Long userId, LocalDate from, LocalDate to, long offset, int limit) {
            return byUk.values().stream()
                    .filter(row -> activityId == null || activityId.equals(row.getActivityId()))
                    .filter(row -> userId == null || userId.equals(row.getUserId()))
                    .filter(row -> from == null || !row.getSignDate().isBefore(from))
                    .filter(row -> to == null || !row.getSignDate().isAfter(to))
                    .sorted(Comparator.comparing(SgnRecordEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        public List<SgnRecordEntity> all() {
            return new ArrayList<>(byUk.values());
        }
    }
}
