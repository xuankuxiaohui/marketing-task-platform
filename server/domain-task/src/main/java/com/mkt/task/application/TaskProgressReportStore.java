package com.mkt.task.application;

import com.mkt.task.entity.TaskProgressReportEntity;
import java.time.LocalDateTime;

public interface TaskProgressReportStore {

    int insert(TaskProgressReportEntity entity);

    TaskProgressReportEntity getByDedup(long instanceId, String stepCode, String reportId);

    int deleteBefore(LocalDateTime cutoff, int limit);
}
