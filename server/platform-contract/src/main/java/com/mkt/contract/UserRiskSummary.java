package com.mkt.contract;

import java.util.List;

/** Read-only risk facade (D-13). */
public record UserRiskSummary(long hitCount, List<RiskListType> listStatus) {

    public UserRiskSummary {
        listStatus = listStatus == null ? List.of() : List.copyOf(listStatus);
    }
}
