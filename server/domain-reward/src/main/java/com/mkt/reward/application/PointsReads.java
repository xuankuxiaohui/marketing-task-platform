package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import com.mkt.reward.query.PointsAccountQuery;
import com.mkt.reward.query.PointsTransactionQuery;
import com.mkt.reward.response.PointsAccountView;
import com.mkt.reward.response.PointsPortalTxView;
import com.mkt.reward.response.PointsTransactionView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Points ledger reads extracted from {@link PointsAppService} (R18-3).
 * Move-only: paging, type normalization, and view mapping unchanged.
 * These reads stay non-transactional, as they were on the service.
 */
@Component
public class PointsReads {

    private final PointsStore store;

    @Autowired
    public PointsReads(PointsStore store) {
        this.store = store;
    }

    PageData<PointsAccountView> pageAccounts(PointsAccountQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = store.countAccounts(query.userId());
        List<PointsAccountView> records = store.listAccounts(query.userId(), page.offset(), page.pageSize()).stream()
                .map(PointsReads::toAccountView)
                .toList();
        return new PageData<>(total, records);
    }

    PageData<PointsTransactionView> pageTransactions(PointsTransactionQuery query) {
        String type = normalizeType(query.type());
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        LocalDateTime from = RewardTime.toUtc(query.from());
        LocalDateTime to = RewardTime.toUtc(query.to());
        long total = store.countTx(query.userId(), type, from, to);
        List<PointsTransactionView> records = store.listTx(
                        query.userId(), type, from, to, page.offset(), page.pageSize())
                .stream()
                .map(PointsReads::toTxView)
                .toList();
        return new PageData<>(total, records);
    }

    PageData<PointsPortalTxView> pagePortal(long userId, String type, Integer page, Integer pageSize) {
        String normalized = normalizeType(type);
        PageQuery pager = PageQuery.of(page, pageSize);
        long total = store.countTx(userId, normalized, null, null);
        List<PointsPortalTxView> records = store.listTx(userId, normalized, null, null, pager.offset(), pager.pageSize())
                .stream()
                .map(PointsReads::toPortalView)
                .toList();
        return new PageData<>(total, records);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (!PointTypes.closed(type)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return type;
    }

    private static PointsAccountView toAccountView(PntAccountEntity row) {
        return new PointsAccountView(
                row.getId(),
                row.getUserId(),
                row.getBalance() == null ? 0L : row.getBalance(),
                RewardTime.toInstant(row.getCreatedAt()),
                RewardTime.toInstant(row.getUpdatedAt()));
    }

    private static PointsTransactionView toTxView(PntTransactionEntity row) {
        return new PointsTransactionView(
                row.getId(),
                row.getUserId(),
                row.getType(),
                row.getAmount() == null ? 0L : row.getAmount(),
                row.getBalanceAfter() == null ? 0L : row.getBalanceAfter(),
                row.getBizSource(),
                row.getBizId(),
                RewardTime.toInstant(row.getExpireAt()),
                row.getRemark(),
                row.simulatedFlag(),
                RewardTime.toInstant(row.getCreatedAt()));
    }

    private static PointsPortalTxView toPortalView(PntTransactionEntity row) {
        return new PointsPortalTxView(
                row.getType(),
                row.getAmount() == null ? 0L : row.getAmount(),
                row.getBalanceAfter() == null ? 0L : row.getBalanceAfter(),
                row.getBizSource(),
                null,
                row.getRemark(),
                RewardTime.toInstant(row.getCreatedAt()));
    }
}
