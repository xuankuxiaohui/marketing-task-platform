package com.mkt.reward.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PrizeTabs;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.response.PrizeCardView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** C-end prize list (design §4.9.3). */
@Service
public class PrizePortalAppService {

    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final PrizeSourceLookup sources;

    public PrizePortalAppService(GrantRecordStore grants, PrizeStore prizes) {
        this(grants, prizes, (PrizeSourceLookup) null);
    }

    @Autowired
    public PrizePortalAppService(
            GrantRecordStore grants, PrizeStore prizes, ObjectProvider<PrizeSourceLookup> sources) {
        this(grants, prizes, sources == null ? null : sources.getIfAvailable());
    }

    public PrizePortalAppService(GrantRecordStore grants, PrizeStore prizes, PrizeSourceLookup sources) {
        this.grants = grants;
        this.prizes = prizes;
        this.sources = sources;
    }

    public PageData<PrizeCardView> list(long userId, String tab, Integer page, Integer pageSize) {
        boolean pendingOnly = !PrizeTabs.ALL.equals(tab);
        PageQuery pager = PageQuery.of(page, pageSize);
        long total = grants.countPortalPrizes(userId, pendingOnly);
        List<GrantRecordEntity> rows = grants.listPortalPrizes(userId, pendingOnly, pager.offset(), pager.pageSize());
        List<PrizeCardView> views = new ArrayList<>();
        for (GrantRecordEntity row : rows) {
            views.add(toView(row));
        }
        return new PageData<>(total, views);
    }

    private PrizeCardView toView(GrantRecordEntity row) {
        PrizeEntity prize = prizes.getByIdIncludingDeleted(row.getPrizeId());
        PrizeSourceLookup.SourceRef source = sources == null
                ? PrizeSourceLookup.SourceRef.empty()
                : nullToEmpty(sources.resolve(row.getGrantSource(), row.getSourceId()));
        return new PrizeCardView(
                row.getId(),
                prize == null ? row.getPrizeCode() : prize.getName(),
                prize == null ? null : prize.getImageUrl(),
                categoryCode(row, prize),
                prize == null ? null : prize.getRewardTarget(),
                prize == null ? null : prize.getFulfillmentMode(),
                row.getStatus(),
                row.getFulfillmentStatus(),
                RewardTime.toInstant(row.getExpireAt()),
                RewardTime.toInstant(row.getCreatedAt() != null ? row.getCreatedAt() : row.getGrantedAt()),
                source.sourceTaskId(),
                source.sourceTaskName(),
                row.getFailReason(),
                row.getFulfillFailReason(),
                RewardTime.toInstant(row.getClaimedAt()),
                source.activityId(),
                source.activityName());
    }

    private static String categoryCode(GrantRecordEntity row, PrizeEntity prize) {
        if (row.getCategoryCode() != null && !row.getCategoryCode().isBlank()) {
            return row.getCategoryCode();
        }
        return prize == null ? null : prize.getCategoryCode();
    }

    private static PrizeSourceLookup.SourceRef nullToEmpty(PrizeSourceLookup.SourceRef ref) {
        return ref == null ? PrizeSourceLookup.SourceRef.empty() : ref;
    }
}
