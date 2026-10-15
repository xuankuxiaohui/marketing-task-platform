package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.response.BatchItemResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Batch publish and offline extracted from {@link TaskPublishAppService} (R19-6).
 * Move-only: batchPublish / batchOffline semantics unchanged.
 */
@Component
public class PublishBatch {

    private final PublishExecute publishExecute;
    private final PublishOffline publishOffline;
    private final ObjectProvider<PlatformTransactionManager> txm;
    private final TransactionTemplate txDirect;

    @Autowired
    public PublishBatch(
            PublishExecute publishExecute,
            PublishOffline publishOffline,
            ObjectProvider<PlatformTransactionManager> txm) {
        this.publishExecute = publishExecute;
        this.publishOffline = publishOffline;
        this.txm = txm;
        this.txDirect = null;
    }

    PublishBatch(PublishExecute publishExecute, PublishOffline publishOffline, TransactionTemplate tx) {
        this.publishExecute = publishExecute;
        this.publishOffline = publishOffline;
        this.txm = null;
        this.txDirect = tx;
    }

    List<BatchItemResponse> batchPublish(List<Long> ids) {
        return batch(ids, id -> publishExecute.publish(id, new PublishCommand(true, false)));
    }

    List<BatchItemResponse> batchOffline(List<Long> ids) {
        return batch(ids, id -> publishOffline.offline(id));
    }

    private List<BatchItemResponse> batch(List<Long> ids, IdAction action) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "ids 不能为空");
        }
        if (ids.size() > 50) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "单批最多 50 条");
        }
        List<BatchItemResponse> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                out.add(BatchItemResponse.fail(0, CommonErrorCodes.PARAM_INVALID.code()));
                continue;
            }
            try {
                runInTx(() -> action.run(id));
                out.add(BatchItemResponse.ok(id));
            } catch (BusinessException ex) {
                out.add(BatchItemResponse.fail(id, ex.errorCode().code()));
            }
        }
        return out;
    }

    private void runInTx(Runnable action) {
        runInTx(() -> {
            action.run();
            return true;
        });
    }

    private boolean runInTx(BooleanSupplier action) {
        TransactionTemplate template = tx();
        if (template != null) {
            Boolean result = template.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }

    private TransactionTemplate tx() {
        if (txDirect != null) {
            return txDirect;
        }
        if (txm == null) {
            return null;
        }
        PlatformTransactionManager manager = txm.getIfAvailable();
        return manager == null ? null : new TransactionTemplate(manager);
    }

    @FunctionalInterface
    private interface IdAction {
        void run(long id);
    }
}
