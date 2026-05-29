package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.vo.TaskFilterVO;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Filter CRUD", description = "过滤器规则管理")
@RestController
@RequestMapping("/api/admin/task/{taskId}/filters")
@RequiredArgsConstructor
public class AdminFilterCrudController {
    private final TaskFilterMapper taskFilterMapper;
    private final TaskDefinitionCacheService cacheService;

    @Operation(summary = "获取过滤器列表")
    @GetMapping
    public Result<List<TaskFilterVO>> list(@PathVariable Long taskId) {
        List<TaskFilter> filters = taskFilterMapper.selectList(
                new LambdaQueryWrapper<TaskFilter>()
                        .eq(TaskFilter::getTaskId, taskId)
                        .orderByAsc(TaskFilter::getSeq));
        return Result.ok(filters.stream().map(TaskFilterVO::from).toList());
    }

    @Operation(summary = "获取过滤器详情")
    @GetMapping("/{filterId}")
    public Result<TaskFilterVO> getById(@PathVariable Long taskId, @PathVariable Long filterId) {
        TaskFilter filter = taskFilterMapper.selectById(filterId);
        if (filter == null || !filter.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "过滤器不存在");
        }
        return Result.ok(TaskFilterVO.from(filter));
    }

    @Operation(summary = "创建过滤器")
    @PostMapping
    public Result<TaskFilterVO> create(@PathVariable Long taskId, @Valid @RequestBody TaskFilterVO vo) {
        TaskFilter filter = vo.toEntity();
        filter.setId(null);
        filter.setTaskId(taskId);
        if (filter.getSeq() == null) {
            Integer maxSeq = taskFilterMapper.selectList(
                    new LambdaQueryWrapper<TaskFilter>()
                            .eq(TaskFilter::getTaskId, taskId)
                            .orderByDesc(TaskFilter::getSeq)
                            .last("LIMIT 1"))
                    .stream()
                    .findFirst()
                    .map(TaskFilter::getSeq)
                    .orElse(null);
            filter.setSeq(maxSeq == null ? 1 : maxSeq + 1);
        }
        taskFilterMapper.insert(filter);
        cacheService.evict(taskId);
        return Result.ok(TaskFilterVO.from(filter));
    }

    @Operation(summary = "更新过滤器")
    @PutMapping("/{filterId}")
    public Result<TaskFilterVO> update(@PathVariable Long taskId, @PathVariable Long filterId,
                                       @Valid @RequestBody TaskFilterVO vo) {
        TaskFilter existing = taskFilterMapper.selectById(filterId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "过滤器不存在");
        }
        TaskFilter filter = vo.toEntity();
        filter.setId(filterId);
        filter.setTaskId(taskId);
        taskFilterMapper.updateById(filter);
        cacheService.evict(taskId);
        return Result.ok(TaskFilterVO.from(taskFilterMapper.selectById(filterId)));
    }

    @Operation(summary = "删除过滤器")
    @DeleteMapping("/{filterId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long filterId) {
        TaskFilter existing = taskFilterMapper.selectById(filterId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "过滤器不存在");
        }
        taskFilterMapper.deleteById(filterId);
        cacheService.evict(taskId);
        return Result.ok(null);
    }
}
