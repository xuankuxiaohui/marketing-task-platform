package com.marketing.task.controller.admin;

import com.marketing.common.Result;
import com.marketing.task.domain.dto.FilterValidateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.service.filter.FilterExpressionEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Filters", description = "过滤器管理")
@RestController
@RequestMapping("/api/admin/filter")
@RequiredArgsConstructor
public class AdminFilterController {
    private final FilterExpressionEngine expressionEngine;

    @Operation(summary = "验证过滤器表达式")
    @PostMapping("/validate")
    public Result<Void> validate(@Valid @RequestBody FilterValidateRequest request) {
        expressionEngine.validate(request.getExpression());
        return Result.ok(null);
    }
}
