package com.marketing.task.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // ---- Generic (1000-1999) ----
    BAD_REQUEST(1000, 400, "BAD_REQUEST", "参数错误"),
    UNAUTHORIZED(1001, 401, "UNAUTHORIZED", "未授权"),
    FORBIDDEN(1002, 403, "FORBIDDEN", "无权限"),
    NOT_FOUND(1003, 404, "NOT_FOUND", "资源不存在"),
    INTERNAL_ERROR(1004, 500, "INTERNAL_ERROR", "系统错误"),

    // ---- Auth (2000-2999) ----
    AUTH_REQUIRED(2000, 401, "AUTH_001", "缺少用户上下文"),
    USER_EXISTS(2001, 409, "AUTH_002", "用户名已存在"),

    // ---- Captcha (3000-3999) ----
    CAPTCHA_MISSING(3000, 400, "CAPTCHA_001", "验证码参数缺失"),
    CAPTCHA_EXPIRED(3001, 400, "CAPTCHA_002", "验证码已过期，请刷新后重试"),
    CAPTCHA_WRONG(3002, 400, "CAPTCHA_003", "验证码错误"),

    // ---- Task (4000-4999) ----
    TASK_NOT_FOUND(4000, 404, "TASK_001", "任务不存在"),
    INVALID_PARAM(4001, 422, "TASK_002", "参数错误"),
    INVALID_STATUS(4002, 422, "TASK_003", "任务状态不允许此操作"),

    // ---- Step (5000-5999) ----
    STEP_NOT_FOUND(5000, 404, "STEP_001", "步骤不存在"),
    STEP_TYPE_MISMATCH(5001, 422, "STEP_002", "步骤类型不匹配"),
    CALLBACK_KEY_MISMATCH(5002, 422, "STEP_003", "回调事件Key不匹配"),

    // ---- Instance/Mutex (6000-6999) ----
    INSTANCE_NOT_FOUND(6000, 404, "INSTANCE_001", "实例不存在"),
    INSTANCE_PARAM_INSUFFICIENT(6001, 400, "INSTANCE_002", "参数不足：需提供instanceId或(userId+taskId+cycleKey)"),
    MUTEX_CONFLICT(6002, 409, "MUTEX_001", "互斥任务冲突，请先完成正在进行中的同类任务"),
    MUTEX_GROUP_NOT_EMPTY(6003, 409, "MUTEX_002", "互斥组下仍有任务，无法删除"),

    // ---- Cycle (7000-7999) ----
    SPECIAL_CYCLE_KEY_MISSING(7000, 422, "CYCLE_001", "SPECIAL任务缺少specialCycleKey"),

    // ---- Prize/Reward (8000-8999) ----
    PRIZE_NOT_FOUND(8000, 404, "PRIZE_001", "奖品不存在"),
    PRIZE_RECORD_NOT_FOUND(8001, 404, "PRIZE_002", "中奖记录不存在"),
    PRIZE_CONFIG_MISSING(8002, 404, "PRIZE_003", "奖品配置不存在"),
    PRIZE_HANDLER_VALIDATION(8003, 422, "PRIZE_004", "奖品参数校验失败"),
    PRIZE_HANDLER_ERROR(8004, 500, "PRIZE_005", "奖品发放失败"),
    PRIZE_CLAIM_EXPIRED(8005, 410, "PRIZE_006", "奖品已过期"),
    PRIZE_RECORD_INVALID_STATUS(8006, 409, "PRIZE_007", "奖品记录状态不允许此操作"),
    REWARD_HANDLER_NOT_FOUND(8007, 500, "REWARD_001", "未找到匹配的发奖处理器"),

    // ---- Filter (9000-9999) ----
    FILTER_EXPRESSION_EMPTY(9000, 422, "FILTER_001", "过滤表达式不能为空"),
    FILTER_EXPRESSION_TOO_LONG(9001, 422, "FILTER_002", "过滤表达式长度不能超过1024字符"),
    FILTER_EXPRESSION_DISALLOWED(9002, 422, "FILTER_003", "过滤表达式包含禁用关键字");

    private final int code;
    private final int httpStatus;
    private final String subCode;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String subCode, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.subCode = subCode;
        this.defaultMessage = defaultMessage;
    }
}
