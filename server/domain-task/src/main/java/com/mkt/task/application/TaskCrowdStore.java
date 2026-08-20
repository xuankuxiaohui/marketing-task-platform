package com.mkt.task.application;

import com.mkt.task.entity.TaskCrowdEntity;
import java.util.List;

public interface TaskCrowdStore {

    TaskCrowdEntity getById(long id);

    TaskCrowdEntity getByCode(String code);

    int insert(TaskCrowdEntity entity);

    int update(TaskCrowdEntity entity);

    int deleteById(long id);

    long countAll();

    List<TaskCrowdEntity> list(long offset, int limit);

    int countMember(long crowdId, long userId);

    int insertMemberIgnore(long crowdId, long userId);

    int deleteMembers(long crowdId);

    List<Long> listUserIds(long crowdId, long offset, int limit);
}
