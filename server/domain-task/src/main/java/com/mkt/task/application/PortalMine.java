package com.mkt.task.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.response.MineTaskView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Mine extracted from {@link TaskPortalAppService} (R17-2).
 * Move-only: mine semantics unchanged.
 */
@Component
public class PortalMine {

    private final TaskInstanceStore instances;
    private final TaskPortalPublishedCatalog publishedCatalog;
    private final TaskPortalCardViews cardViews;

    @Autowired
    public PortalMine(
            TaskInstanceStore instances,
            TaskPortalPublishedCatalog publishedCatalog,
            TaskPortalCardViews cardViews) {
        this.instances = instances;
        this.publishedCatalog = publishedCatalog;
        this.cardViews = cardViews;
    }

    PageData<MineTaskView> mine(long userId, String status, String category, Integer page, Integer pageSize) {
        PageQuery query = PageQuery.of(page, pageSize);
        List<Long> categoryTaskIds = null;
        String categoryFilter = allCategoryToNull(category);
        if (categoryFilter != null) {
            categoryTaskIds = new ArrayList<>();
            TaskPortalPublishedCatalog.PublishedBundle publishedBundle = publishedCatalog.publishedBundle();
            List<TaskDefinitionEntity> published = publishedBundle.definitions();
            Map<Long, SnapshotContent> publishedSnaps = publishedBundle.snapshots();
            for (TaskDefinitionEntity definition : published) {
                SnapshotContent snapshot = publishedSnaps.get(definition.getId());
                if (snapshot != null && categoryFilter.equals(snapshot.category())) {
                    categoryTaskIds.add(definition.getId());
                }
            }
            List<TaskInstanceEntity> history = instances.listByUser(userId);
            List<Long> snapIds = new ArrayList<>();
            for (TaskInstanceEntity row : history) {
                if (categoryTaskIds.contains(row.getTaskId()) || row.getSnapshotId() == null) {
                    continue;
                }
                snapIds.add(row.getSnapshotId());
            }
            Map<Long, SnapshotContent> historySnaps = cardViews.loadSnapshotsByIds(snapIds);
            for (TaskInstanceEntity row : history) {
                if (categoryTaskIds.contains(row.getTaskId()) || row.getSnapshotId() == null) {
                    continue;
                }
                SnapshotContent content = historySnaps.get(row.getSnapshotId());
                if (content != null && categoryFilter.equals(content.category())) {
                    categoryTaskIds.add(row.getTaskId());
                }
            }
            if (categoryTaskIds.isEmpty()) {
                return new PageData<>(0, List.of());
            }
        }
        String statusFilter = InstanceStatuses.mineFilter(status);
        long total = instances.countMine(userId, statusFilter, categoryTaskIds);
        List<TaskInstanceEntity> rows =
                instances.listMine(userId, statusFilter, categoryTaskIds, query.offset(), query.pageSize());
        List<MineTaskView> views = new ArrayList<>(rows.size());
        for (TaskInstanceEntity row : rows) {
            views.add(cardViews.toMine(row));
        }
        return new PageData<>(total, views);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String allCategoryToNull(String category) {
        String raw = blankToNull(category);
        if (raw == null || "ALL".equalsIgnoreCase(raw) || InstanceStatuses.tabIndexLeak(raw)) {
            return null;
        }
        return raw;
    }
}
