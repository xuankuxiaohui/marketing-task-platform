package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.domain.entity.MutexGroup;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.mapper.MutexGroupMapper;
import com.marketing.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MutexGroupService {
    private final MutexGroupMapper mutexGroupMapper;
    private final TaskMapper taskMapper;

    public List<MutexGroup> listAll() {
        return mutexGroupMapper.selectList(null);
    }

    public MutexGroup requireGroup(Long id) {
        MutexGroup group = mutexGroupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return group;
    }

    @Transactional
    public MutexGroup create(MutexGroup group) {
        mutexGroupMapper.insert(group);
        return group;
    }

    @Transactional
    public MutexGroup update(Long id, MutexGroup group) {
        requireGroup(id);
        group.setId(id);
        mutexGroupMapper.updateById(group);
        return mutexGroupMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        requireGroup(id);
        long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getMutexGroupId, id));
        if (taskCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "互斥组下仍有 " + taskCount + " 个任务，无法删除");
        }
        mutexGroupMapper.deleteById(id);
    }

    public int countTasks(Long groupId) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getMutexGroupId, groupId));
        return count.intValue();
    }
}
