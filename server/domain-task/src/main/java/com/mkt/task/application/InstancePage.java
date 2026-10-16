package com.mkt.task.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkt.kernel.PageData;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceView;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admin instance page extracted from {@link TaskInstanceAppService} (R24-1).
 * Move-only: page semantics unchanged.
 */
@Component
public class InstancePage {

    private final TaskInstanceStore instances;
    private final InstanceViews views;

    /**
     * Architect 2026-10-12: admin-list instances 减压 — COUNT(*) on ~500k rows avg≈493ms
     * under load (P95 near 800). Caffeine TTL 3s + {@code get(key, loader)} singleflight
     * coalesces concurrent unfiltered/filter counts. List query stays live (PK DESC LIMIT).
     * Caps locked (Tomcat120; Redisson≤32; portal 1536M not raised). 禁降Δ门禁.
     */
    private static final Duration ADMIN_COUNT_TTL = Duration.ofSeconds(3);

    private final Cache<AdminCountKey, Long> adminCountCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(ADMIN_COUNT_TTL)
                    .maximumSize(1_024)
                    .build();

    @Autowired
    public InstancePage(TaskInstanceStore instances, InstanceViews views) {
        this.instances = instances;
        this.views = views;
    }

    PageData<AdminInstanceView> page(InstanceQuery query) {
        LocalDateTime from = TaskTime.toUtc(query.from());
        LocalDateTime to = TaskTime.toUtc(query.to());
        String status = blankToNull(query.status());
        Long taskId = query.taskId();
        Long userId = query.userId();
        Integer simulated = query.simulated();
        AdminCountKey countKey = new AdminCountKey(taskId, userId, status, simulated, from, to);
        long total = adminCountCache.get(
                countKey, k -> instances.countAdmin(k.taskId(), k.userId(), k.status(), k.simulated(), k.from(), k.to()));
        List<TaskInstanceEntity> rows =
                instances.listAdmin(taskId, userId, status, simulated, from, to, query.page().offset(), query.page().pageSize());
        List<AdminInstanceView> views = new ArrayList<>(rows.size());
        for (TaskInstanceEntity row : rows) {
            views.add(this.views.toView(row));
        }
        return new PageData<>(total, views);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Cache key for admin instance COUNT; equals/hash via record components. */
    private record AdminCountKey(
            Long taskId, Long userId, String status, Integer simulated, LocalDateTime from, LocalDateTime to) {}
}
