package com.marketing.task.controller.admin;

import com.marketing.task.common.Result;
import com.marketing.task.domain.dto.FilterValidateRequest;
import com.marketing.task.service.filter.FilterExpressionEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/filter")
@RequiredArgsConstructor
public class AdminFilterController {
    private final FilterExpressionEngine expressionEngine;

    @PostMapping("/validate")
    public Result<Void> validate(@Valid @RequestBody FilterValidateRequest request) {
        expressionEngine.validate(request.getExpression());
        return Result.ok(null);
    }
}
