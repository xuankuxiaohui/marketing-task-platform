package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FilterValidateRequest {
    @NotBlank(message = "表达式不能为空")
    private String expression;
}
