package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.MutexGroup;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.vo.MutexGroupVO;
import com.marketing.task.domain.vo.TaskAdminVO;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.service.task.MutexGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mutex-groups")
@RequiredArgsConstructor
public class AdminMutexGroupController {
    private final MutexGroupService mutexGroupService;
    private final TaskMapper taskMapper;

    @GetMapping
    public Result<List<MutexGroupVO>> list() {
        List<MutexGroup> groups = mutexGroupService.listAll();
        List<MutexGroupVO> vos = groups.stream().map(g -> {
            MutexGroupVO vo = MutexGroupVO.from(g);
            vo.setTaskCount(mutexGroupService.countTasks(g.getId()));
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @GetMapping("/{id}")
    public Result<MutexGroupVO> getById(@PathVariable Long id) {
        MutexGroup group = mutexGroupService.requireGroup(id);
        MutexGroupVO vo = MutexGroupVO.from(group);
        vo.setTaskCount(mutexGroupService.countTasks(id));
        return Result.ok(vo);
    }

    @GetMapping("/{id}/tasks")
    public Result<List<TaskAdminVO>> getTasks(@PathVariable Long id) {
        mutexGroupService.requireGroup(id);
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getMutexGroupId, id)
                .orderByAsc(Task::getCode));
        return Result.ok(tasks.stream().map(TaskAdminVO::from).toList());
    }

    @PostMapping
    public Result<MutexGroupVO> create(@RequestBody MutexGroup group) {
        MutexGroup created = mutexGroupService.create(group);
        return Result.ok(MutexGroupVO.from(created));
    }

    @PutMapping("/{id}")
    public Result<MutexGroupVO> update(@PathVariable Long id, @RequestBody MutexGroup group) {
        MutexGroup updated = mutexGroupService.update(id, group);
        return Result.ok(MutexGroupVO.from(updated));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mutexGroupService.delete(id);
        return Result.ok(null);
    }
}
