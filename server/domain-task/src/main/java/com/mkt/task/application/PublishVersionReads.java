package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Version list and diff extracted from {@link TaskPublishAppService} (R27-1).
 * Move-only: versions / diff semantics unchanged.
 * {@code requireLive} moved with this read path; publish, schedule, batch, and scan do not use it.
 */
@Component
public class PublishVersionReads {

    private final TaskDefinitionStore definitions;
    private final TaskPublishVersionDiff versionDiff;

    @Autowired
    public PublishVersionReads(TaskDefinitionStore definitions, TaskPublishVersionDiff versionDiff) {
        this.definitions = definitions;
        this.versionDiff = versionDiff;
    }

    List<TaskVersionView> versions(long id) {
        requireLive(id);
        return versionDiff.listVersions(id);
    }

    VersionDiffResponse diff(long id, Integer left, Integer right) {
        requireLive(id);
        if (left == null || right == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "left 与 right 必填");
        }
        return versionDiff.diff(id, left, right);
    }

    private TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
