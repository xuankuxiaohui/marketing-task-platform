package com.marketing.task.service.cycle;

import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.enums.PeriodType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class CycleKeyResolver {
    private static final DateTimeFormatter DAILY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTHLY = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter CRON = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public String resolve(Task task) {
        LocalDateTime now = LocalDateTime.now();
        PeriodType periodType = PeriodType.valueOf(task.getPeriodType());
        return switch (periodType) {
            case ONCE -> "ONCE";
            case DAILY -> now.format(DAILY);
            case MONTHLY -> now.format(MONTHLY);
            case CRON -> now.format(CRON);
            case SPECIAL -> {
                if (task.getSpecialCycleKey() == null || task.getSpecialCycleKey().isBlank()) {
                    throw new BusinessException("SPECIAL任务缺少specialCycleKey");
                }
                yield task.getSpecialCycleKey();
            }
        };
    }
}
