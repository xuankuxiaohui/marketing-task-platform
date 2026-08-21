package com.mkt.task.application;

import com.mkt.task.entity.TaskProgressReportEntity;
import com.mkt.task.mapper.TaskProgressReportMapper;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskProgressReportStore implements TaskProgressReportStore {

    private final TaskProgressReportMapper reports;

    public MybatisTaskProgressReportStore(TaskProgressReportMapper reports) {
        this.reports = reports;
    }

    @Override
    public int insert(TaskProgressReportEntity entity) {
        try {
            return reports.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (duplicate(ex)) {
                throw new DuplicateKeyException("uk_dedup", ex);
            }
            throw ex;
        }
    }

    private static boolean duplicate(Throwable ex) {
        while (ex != null) {
            if (ex instanceof DuplicateKeyException || ex instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    @Override
    public TaskProgressReportEntity getByDedup(long instanceId, String stepCode, String reportId) {
        return reports.selectByDedup(instanceId, stepCode, reportId);
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff, int limit) {
        return reports.deleteBefore(cutoff, limit);
    }
}
