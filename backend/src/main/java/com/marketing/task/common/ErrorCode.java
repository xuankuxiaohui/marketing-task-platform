package com.marketing.task.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // ---- Generic ----
    BAD_REQUEST(400, "BAD_REQUEST", "参数错误"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "未授权"),
    FORBIDDEN(403, "FORBIDDEN", "无权限"),
    NOT_FOUND(404, "NOT_FOUND", "资源不存在"),
    CONFLICT(409, "CONFLICT", "资源冲突"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "系统错误"),

    // ---- Auth ----
    AUTH_REQUIRED(401, "AUTH_001", "缺少用户上下文"),
    USER_EXISTS(409, "AUTH_002", "用户名已存在"),

    // ---- Captcha ----
    CAPTCHA_MISSING(400, "CAPTCHA_001", "验证码参数缺失"),
    CAPTCHA_EXPIRED(400, "CAPTCHA_002", "验证码已过期，请刷新后重试"),
    CAPTCHA_WRONG(400, "CAPTCHA_003", "验证码错误"),

    // ---- Task ----
    TASK_NOT_FOUND(404, "TASK_001", "任务不存在"),

    // ---- Step ----
    STEP_NOT_FOUND(404, "STEP_001", "步骤不存在"),
    STEP_TYPE_MISMATCH(400, "STEP_002", "步骤类型不匹配"),
    CALLBACK_KEY_MISMATCH(400, "STEP_003", "回调事件Key不匹配"),

    // ---- Instance ----
    INSTANCE_NOT_FOUND(404, "INSTANCE_001", "实例不存在"),
    INSTANCE_PARAM_INSUFFICIENT(400, "INSTANCE_002", "参数不足：需提供instanceId或(userId+taskId+cycleKey)"),
    MUTEX_CONFLICT(409, "MUTEX_001", "互斥任务冲突，请先完成正在进行中的同类任务"),

    // ---- Cycle ----
    SPECIAL_CYCLE_KEY_MISSING(400, "CYCLE_001", "SPECIAL任务缺少specialCycleKey"),

    // ---- Prize ----
    PRIZE_NOT_FOUND(404, "PRIZE_001", "奖品不存在"),
    PRIZE_RECORD_NOT_FOUND(404, "PRIZE_002", "中奖记录不存在"),
    PRIZE_CONFIG_MISSING(404, "PRIZE_003", "奖品配置不存在"),
    PRIZE_HANDLER_VALIDATION(400, "PRIZE_004", "奖品参数校验失败"),
    PRIZE_HANDLER_ERROR(500, "PRIZE_005", "奖品发放失败"),
    PRIZE_CLAIM_EXPIRED(400, "PRIZE_006", "奖品已过期"),

    // ---- Reward ----
    REWARD_HANDLER_NOT_FOUND(500, "REWARD_001", "未找到匹配的发奖处理器"),

    // ---- Filter ----
    FILTER_EXPRESSION_EMPTY(400, "FILTER_001", "过滤表达式不能为空"),
    FILTER_EXPRESSION_TOO_LONG(400, "FILTER_002", "过滤表达式长度不能超过1024字符"),
    FILTER_EXPRESSION_DISALLOWED(400, "FILTER_003", "过滤表达式包含禁用关键字"),
    FILTER_NOT_IMPLEMENTED(501, "FILTER_004", "过滤功能暂未实现");

    private final int code;
    private final String subCode;
    private final String defaultMessage;

    ErrorCode(int code, String subCode, String defaultMessage) {
        this.code = code;
        this.subCode = subCode;
        this.defaultMessage = defaultMessage;
    }
}
