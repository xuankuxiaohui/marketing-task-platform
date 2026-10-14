package com.mkt.reward.application;

import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fulfillment lookup helpers extracted from {@link FulfillmentService} (R13-1).
 * Move-only: prizeOf / categoryOf / reconRequired / mappedFailReason semantics unchanged.
 */
@Component
public class FulfillmentLookups {

    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;

    @Autowired
    public FulfillmentLookups(
            ObjectProvider<PrizeStore> prizes, ObjectProvider<PrizeCategoryStore> categories) {
        this(prizes.getIfAvailable(), categories.getIfAvailable());
    }

    public FulfillmentLookups(PrizeStore prizes, PrizeCategoryStore categories) {
        this.prizes = prizes;
        this.categories = categories;
    }

    PrizeEntity prizeOf(GrantRecordEntity row) {
        if (prizes == null || row.getPrizeId() == null) {
            return null;
        }
        return prizes.getByIdIncludingDeleted(row.getPrizeId());
    }

    PrizeCategoryEntity categoryOf(PrizeEntity prize) {
        if (categories == null || prize == null || prize.getCategoryCode() == null) {
            return null;
        }
        return categories.getByCode(prize.getCategoryCode());
    }

    boolean reconRequired(GrantRecordEntity row) {
        PrizeCategoryEntity category = categoryOf(prizeOf(row));
        if (category == null && categories != null && row.getCategoryCode() != null) {
            category = categories.getByCode(row.getCategoryCode());
        }
        return category != null && category.reconRequiredFlag();
    }

    static String mappedFailReason(String failReason) {
        if (failReason != null
                && FulfillFailReasons.closed(failReason)
                && !FulfillFailReasons.MANUAL.equals(failReason)
                && !FulfillFailReasons.TIMEOUT.equals(failReason)) {
            return failReason;
        }
        return FulfillFailReasons.CALLBACK_FAILED;
    }
}
