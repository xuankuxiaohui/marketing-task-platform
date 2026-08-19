package com.mkt.task.application;

import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.mapper.TaskMutexGroupMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskMutexGroupStore implements TaskMutexGroupStore {

    private final TaskMutexGroupMapper mapper;

    public MybatisTaskMutexGroupStore(TaskMutexGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TaskMutexGroupEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public TaskMutexGroupEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(TaskMutexGroupEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(TaskMutexGroupEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    @Override
    public long countAll() {
        return mapper.selectCountAll();
    }

    @Override
    public List<TaskMutexGroupEntity> list(long offset, int limit) {
        return mapper.selectPage(offset, limit);
    }
}
