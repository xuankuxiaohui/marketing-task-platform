package com.mkt.identity.convert;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.UserRiskSummary;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.response.PortalUserDetailResponse;
import com.mkt.identity.response.PortalUserView;
import com.mkt.identity.response.PrizeSummaryView;
import java.util.List;

public final class PortalUserConvert {

    private PortalUserConvert() {}

    public static PortalUserView toView(PortalUserEntity entity) {
        return new PortalUserView(
                entity.getId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getProvince(),
                entity.getUserLevel(),
                entity.getUserRole(),
                UserAttributeConvert.parseTags(entity.getTags()),
                entity.getOrgId(),
                entity.getStatus(),
                IdentityTime.toInstant(entity.getRegisteredAt()),
                IdentityTime.toInstant(entity.getLastLoginAt()),
                IdentityTime.toInstant(entity.getCreatedAt()));
    }

    public static PortalUserDetailResponse toDetail(
            PortalUserEntity entity,
            InstanceCounts counts,
            long pointsBalance,
            PrizeSummary prizes,
            UserRiskSummary risk) {
        InstanceCounts safeCounts = counts == null ? new InstanceCounts(0L, 0L) : counts;
        PrizeSummary safePrizes = prizes == null ? new PrizeSummary(0L, 0L) : prizes;
        UserRiskSummary safeRisk = risk == null ? new UserRiskSummary(0L, List.of()) : risk;
        return new PortalUserDetailResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getProvince(),
                entity.getUserLevel(),
                entity.getUserRole(),
                UserAttributeConvert.parseTags(entity.getTags()),
                entity.getOrgId(),
                entity.getStatus(),
                IdentityTime.toInstant(entity.getRegisteredAt()),
                IdentityTime.toInstant(entity.getLastLoginAt()),
                IdentityTime.toInstant(entity.getCreatedAt()),
                safeCounts.inProgressInstanceCount(),
                safeCounts.historyInstanceCount(),
                pointsBalance,
                new PrizeSummaryView(safePrizes.won(), safePrizes.granted()),
                safeRisk.hitCount(),
                safeRisk.listStatus());
    }
}
