package com.mkt.reward.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Reward error codes (design §3.9 / §4.5). */
public enum RewardErrorCodes implements ErrorCode {
    CATEGORY_DUPLICATE_CODE("reward.category.duplicate-code", 400, "奖品分类编码已存在"),
    CATEGORY_BUILTIN_PROTECTED("reward.category.builtin-protected", 400, "内置分类不可删除"),
    PRIZE_DUPLICATE_CODE("reward.prize.duplicate-code", 400, "奖品编码已存在"),
    PRIZE_POINTS_AMOUNT_REQUIRED("reward.prize.points-amount-required", 400, "积分类奖品必须填写正整数 points"),
    PRIZE_FACE_REQUIRED("reward.prize.face-required", 400, "面额类奖品必须填写正整数 faceFen"),
    PRIZE_COST_REQUIRED("reward.prize.cost-required", 400, "固定单价类奖品必须填写正整数 unitCostFen"),
    PRIZE_LIMIT_NEGATIVE("reward.prize.limit-negative", 400, "领取限制不能为负数"),
    PRIZE_CATEGORY_DISABLED("reward.prize.category-disabled", 400, "奖品分类已停用"),
    PRIZE_ADAPTER_REQUIRED("reward.prize.adapter-required", 400, "第三方分类必须指定适配器"),
    PRIZE_REFERENCED_BY_SNAPSHOT("reward.prize.referenced-by-snapshot", 400, "奖品已被在线版本快照引用，不可删除"),
    PRIZE_DISABLED("reward.prize.disabled", 400, "奖品已停用"),
    STOCK_INSUFFICIENT("reward.stock.insufficient", 400, "库存不足"),
    CLAIM_LIMIT_EXCEEDED("reward.claim.limit-exceeded", 400, "领取次数已达上限");

    private final String code;
    private final int httpStatus;
    private final String message;

    RewardErrorCodes(String code, int httpStatus, String message) {
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
