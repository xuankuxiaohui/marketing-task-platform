package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/instance")
@RequiredArgsConstructor
public class AdminInstanceController {
    private final UserTaskInstanceMapper instanceMapper;

    @GetMapping
    public Result<IPage<UserTaskInstanceVO>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        IPage<UserTaskInstance> result = instanceMapper.selectPage(Page.of(page, size), null);
        return Result.ok(result.convert(UserTaskInstanceVO::from));
    }
}
