package com.mkt.ad.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Ad error codes (design §3.9 scene {@code position} / {@code material}). */
public enum AdErrorCodes implements ErrorCode {
    POSITION_NOT_FOUND("ad.position.not-found", 404, "广告位不存在"),
    POSITION_CODE_DUPLICATE("ad.position.code-duplicate", 400, "广告位编码已存在"),
    POSITION_FORM_INVALID("ad.position.form-invalid", 400, "广告位形式无效"),
    MATERIAL_NOT_FOUND("ad.material.not-found", 404, "广告素材不存在"),
    MATERIAL_JUMP_INVALID("ad.material.jump-invalid", 400, "跳转配置无效"),
    MATERIAL_WEIGHT_INVALID("ad.material.weight-invalid", 400, "权重须为 1–999"),
    TIME_WINDOW_INVALID("ad.material.time-window-invalid", 400, "排期时间窗无效"),
    PLACEMENT_DUPLICATE("ad.position.placement-duplicate", 400, "该素材已绑定到此广告位"),
    PLACEMENT_NOT_FOUND("ad.position.placement-not-found", 404, "投放关系不存在");

    private final String code;
    private final int httpStatus;
    private final String message;

    AdErrorCodes(String code, int httpStatus, String message) {
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
