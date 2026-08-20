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
import org.springframework.stereotype.Service;

/** C-end prize list (design §4.9.3). */
@Service
public class PrizePortalAppService {

    private final GrantRecordStore grants;
    private final PrizeStore prizes;

    public PrizePortalAppService(GrantRecordStore grants, PrizeStore prizes) {
        this.grants = grants;
        this.prizes = prizes;
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
        return new PrizeCardView(
                row.getId(),
                prize == null ? row.getPrizeCode() : prize.getName(),
                prize == null ? null : prize.getImageUrl(),
                row.getCategoryCode(),
                prize == null ? null : prize.getRewardTarget(),
                prize == null ? null : prize.getFulfillmentMode(),
                row.getStatus(),
                row.getFulfillmentStatus(),
                RewardTime.toInstant(row.getExpireAt()),
                null,
                null,
                row.getFailReason(),
                row.getFulfillFailReason());
    }
}
