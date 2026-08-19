package com.mkt.identity.response;

import com.mkt.contract.RiskListType;
import java.time.Instant;
import java.util.List;

public record PortalUserDetailResponse(
        long id,
        String username,
        String nickname,
        String province,
        String userLevel,
        String userRole,
        List<String> tags,
        String orgId,
        String status,
        Instant registeredAt,
        Instant lastLoginAt,
        Instant createdAt,
        long inProgressInstanceCount,
        long historyInstanceCount,
        long pointsBalance,
        PrizeSummaryView prizeSummary,
        long riskHits,
        List<RiskListType> listStatus) {

    public PortalUserDetailResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        listStatus = listStatus == null ? List.of() : List.copyOf(listStatus);
    }
}
