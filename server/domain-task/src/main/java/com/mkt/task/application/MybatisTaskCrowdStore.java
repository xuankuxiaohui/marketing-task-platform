package com.mkt.task.application;

import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.mapper.TaskCrowdItemMapper;
import com.mkt.task.mapper.TaskCrowdMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskCrowdStore implements TaskCrowdStore {

    private final TaskCrowdMapper crowds;
    private final TaskCrowdItemMapper items;

    public MybatisTaskCrowdStore(TaskCrowdMapper crowds, TaskCrowdItemMapper items) {
        this.crowds = crowds;
        this.items = items;
    }

    @Override
    public TaskCrowdEntity getById(long id) {
        return crowds.selectById(id);
    }

    @Override
    public TaskCrowdEntity getByCode(String code) {
        return crowds.selectByCode(code);
    }

    @Override
    public int insert(TaskCrowdEntity entity) {
        return crowds.insert(entity);
    }

    @Override
    public int update(TaskCrowdEntity entity) {
        return crowds.updateById(entity);
    }

    @Override
    public int deleteById(long id) {
        items.deleteByCrowdId(id);
        return crowds.deleteById(id);
    }

    @Override
    public long countAll() {
        return crowds.selectCountAll();
    }

    @Override
    public List<TaskCrowdEntity> list(long offset, int limit) {
        return crowds.selectPage(offset, limit);
    }

    @Override
    public int countMember(long crowdId, long userId) {
        return items.countByCrowdAndUser(crowdId, userId);
    }

    @Override
    public int insertMemberIgnore(long crowdId, long userId) {
        return items.insertIgnore(crowdId, userId);
    }

    @Override
    public int deleteMembers(long crowdId) {
        return items.deleteByCrowdId(crowdId);
    }

    @Override
    public List<Long> listUserIds(long crowdId, long offset, int limit) {
        return items.selectUserIds(crowdId, offset, limit);
    }
}
