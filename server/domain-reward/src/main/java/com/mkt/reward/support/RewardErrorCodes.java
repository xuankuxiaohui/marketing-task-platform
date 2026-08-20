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
    CLAIM_LIMIT_EXCEEDED("reward.claim.limit-exceeded", 400, "领取次数已达上限"),
    CLAIM_NOT_WON("reward.claim.not-won", 400, "当前状态不可领取"),
    CLAIM_EXPIRED("reward.claim.expired", 400, "奖品已过期"),
    CLAIM_CONFLICT("reward.claim.conflict", 400, "领取冲突，请稍后重试"),
    GRANT_NOT_RETRYABLE("reward.grant.not-retryable", 400, "该发放记录不可重试"),
    GRANT_COMBO_INVALID("reward.grant.combo-invalid", 400, "不满足领取条件"),
    FULFILL_NOT_SENDING("reward.fulfill.not-sending", 400, "履约状态不是发送中"),
    FULFILL_NOT_RETRYABLE("reward.fulfill.not-retryable", 400, "该履约记录不可重试"),
    FULFILL_NOT_FOUND("reward.fulfill.not-found", 404, "履约单不存在"),
    RECON_DUPLICATE_DAY("reward.recon.duplicate-day", 400, "该分类该账单日批次已存在"),
    RECON_ACTION_DONE("reward.recon.action-done", 400, "该差异已处置"),
    RECON_REVIEW_REQUIRED("reward.recon.review-required", 400, "须先核渠确认渠道未出款"),
    RECON_ACTION_FORBIDDEN("reward.recon.action-forbidden", 400, "当前差异不允许该动作"),
    RECON_NOT_PENDING_REVIEW("reward.recon.not-pending-review", 400, "核渠状态不是待核渠"),
    RISK_BLOCKED_GENERIC("risk.blocked.generic", 403, "暂时无法参与"),
    RISK_BLOCKED_ACCOUNT("risk.blocked.account-restricted", 400, "账号受限");

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
