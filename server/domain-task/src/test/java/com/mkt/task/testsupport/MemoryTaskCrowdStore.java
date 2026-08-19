package com.mkt.task.testsupport;

import com.mkt.task.application.TaskCrowdStore;
import com.mkt.task.entity.TaskCrowdEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryTaskCrowdStore implements TaskCrowdStore {

    private final ConcurrentHashMap<Long, TaskCrowdEntity> rows = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Member> members = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public TaskCrowdEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public TaskCrowdEntity getByCode(String code) {
        return rows.values().stream().filter(row -> code.equals(row.getCode())).findFirst().orElse(null);
    }

    @Override
    public int insert(TaskCrowdEntity entity) {
        entity.setId(seq.getAndIncrement());
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int update(TaskCrowdEntity entity) {
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int deleteById(long id) {
        deleteMembers(id);
        return rows.remove(id) == null ? 0 : 1;
    }

    @Override
    public long countAll() {
        return rows.size();
    }

    @Override
    public List<TaskCrowdEntity> list(long offset, int limit) {
        return rows.values().stream()
                .sorted(Comparator.comparing(TaskCrowdEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public int countMember(long crowdId, long userId) {
        return (int) members.stream().filter(m -> m.crowdId == crowdId && m.userId == userId).count();
    }

    @Override
    public int insertMemberIgnore(long crowdId, long userId) {
        if (countMember(crowdId, userId) > 0) {
            return 0;
        }
        members.add(new Member(crowdId, userId));
        return 1;
    }

    @Override
    public int deleteMembers(long crowdId) {
        List<Member> removed = new ArrayList<>();
        members.removeIf(m -> {
            if (m.crowdId == crowdId) {
                removed.add(m);
                return true;
            }
            return false;
        });
        return removed.size();
    }

    @Override
    public List<Long> listUserIds(long crowdId, long offset, int limit) {
        return members.stream()
                .filter(m -> m.crowdId == crowdId)
                .map(m -> m.userId)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private record Member(long crowdId, long userId) {}
}
