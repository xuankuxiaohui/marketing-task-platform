package com.mkt.risk.support;

import com.mkt.contract.RiskListType;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;

/** List-type-specific permission checks (appendix B). */
public interface RiskPermissionGuard {

    void require(String permission);

    default void requireQuery(RiskListType listType) {
        if (listType == null) {
            require(RiskListPermissions.BLACK_QUERY);
            require(RiskListPermissions.WHITE_QUERY);
            return;
        }
        require(RiskListPermissions.query(listType));
    }

    default void requireAdd(RiskListType listType) {
        require(RiskListPermissions.add(listType));
    }

    default void requireRemove(RiskListType listType) {
        require(RiskListPermissions.remove(listType));
    }

    default void requireImport(RiskListType listType) {
        if (listType != RiskListType.BLACK) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "白名单不支持批量导入");
        }
        require(RiskListPermissions.BLACK_IMPORT);
    }

    default void requireCaseQuery() {
        require(RiskListPermissions.CASE_QUERY);
    }

    default void requireCaseHandle() {
        require(RiskListPermissions.CASE_HANDLE);
    }
}
