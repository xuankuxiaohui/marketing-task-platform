package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.TaskDefinitionView;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admin definition page extracted from {@link TaskDefinitionAppService} (R25-1).
 * Move-only: page semantics unchanged.
 */
@Component
public class DefinitionPage {

    private final TaskDefinitionStore definitions;

    @Autowired
    public DefinitionPage(TaskDefinitionStore definitions) {
        this.definitions = definitions;
    }

    PageData<TaskDefinitionView> page(TaskDefinitionQuery query) {
        String code = blankToNull(query.code());
        String name = blankToNull(query.name());
        String status = blankToNull(query.status());
        String category = blankToNull(query.category());
        if (status != null && !DefinitionStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 非法");
        }
        long total = definitions.countByQuery(code, name, status, category);
        List<TaskDefinitionEntity> rows = definitions.listByQuery(
                code, name, status, category, query.page().offset(), query.page().pageSize());
        return new PageData<>(total, rows.stream().map(TaskDefinitionConvert::toView).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
