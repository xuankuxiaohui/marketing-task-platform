package com.marketing.task.controller.internal;

import com.marketing.task.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/task")
public class InternalCallbackController {

    @PostMapping("/callback")
    public Result<Void> callback() {
        return Result.fail(501, "CALLBACK/PROGRESS将在Sprint 2实现");
    }
}
