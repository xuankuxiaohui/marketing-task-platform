package com.mkt.task.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Task error codes (design §3.9 / §4.4). */
public enum TaskErrorCodes implements ErrorCode {
    STEP_CODE_DUPLICATE("task.definition.step-code-duplicate", 400, "步骤编码重复"),
    CYCLE_EMPTY("task.definition.cycle-empty", 400, "周期配置不完整"),
    DAG_CYCLE("task.definition.dag-cycle", 400, "步骤转移存在回跳或成环"),
    REWARD_PRIZE_INVALID("task.definition.reward-prize-invalid", 400, "发奖步骤未引用启用奖品"),
    MUTEX_CYCLE_MISMATCH("task.definition.mutex-cycle-mismatch", 400, "互斥组内周期类型不一致"),
    STEP_COUNT_EXCEEDED("task.definition.step-count-exceeded", 400, "步骤数超过上限"),
    TIME_WINDOW_INVALID("task.definition.time-window-invalid", 400, "时间窗无效"),
    PUBLISHED_NOT_DELETABLE("task.definition.published-not-deletable", 400, "已发布任务不可删除"),
    EXPRESSION_INVALID("task.expression.invalid", 400, "表达式非法"),
    EXPRESSION_VALIDATE_FAILED("task.expression.validate-failed", 400, "表达式校验失败"),
    MUTEX_IN_USE("task.mutex.in-use", 400, "互斥组仍被任务引用"),
    CROWD_SIZE_EXCEEDED("task.crowd.size-exceeded", 400, "人群包条目超过上限"),
    PUBLISH_VALIDATE_FAILED("task.publish.validate-failed", 400, "发布校验失败");

    private final String code;
    private final int httpStatus;
    private final String message;

    TaskErrorCodes(String code, int httpStatus, String message) {
        ErrorCodeFormat.requireValid(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
