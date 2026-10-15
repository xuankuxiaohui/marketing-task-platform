package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.PublishResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Offline extracted from {@link TaskPublishAppService} (R19-5).
 * Move-only: offline semantics unchanged.
 */
@Component
public class PublishOffline {

    private final TaskDefinitionStore definitions;
    private final PublishOfflineExpiry offlineExpiry;
    private final TaskPublishFreeze publishFreeze;
    private final Clock clock;

    @Autowired
    public PublishOffline(
            TaskDefinitionStore definitions,
            PublishOfflineExpiry offlineExpiry,
            TaskPublishFreeze publishFreeze,
            Clock clock) {
        this.definitions = definitions;
        this.offlineExpiry = offlineExpiry;
        this.publishFreeze = publishFreeze;
        this.clock = clock;
    }

    PublishResponse offline(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布任务可下线");
        }
        LocalDateTime now = nowUtc();
        entity.setStatus(DefinitionStatuses.OFFLINE);
        entity.setOfflineAt(now);
        entity.setUpdatedAt(now);
        definitions.update(entity);
        offlineExpiry.recomputeInProgressExpiry(id, now);
        publishFreeze.evictPublishedIndex();
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }
}
