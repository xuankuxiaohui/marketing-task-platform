package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.CrowdMembership;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.RewardPreviewView;
import com.mkt.task.response.TaskCardView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * List extracted from {@link TaskPortalAppService} (R17-1).
 * Move-only: list semantics unchanged.
 */
@Component
public class PortalList {

    private final UserAttributePort users;
    private final TaskPortalDetail detail;
    private final Clock clock;
    private final TaskPortalPublishedCatalog publishedCatalog;
    private final TaskCrowdStore crowds;

    @Autowired
    public PortalList(
            ObjectProvider<UserAttributePort> users,
            TaskPortalDetail detail,
            Clock clock,
            TaskPortalPublishedCatalog publishedCatalog,
            TaskCrowdStore crowds) {
        this(users.getIfAvailable(), detail, clock, publishedCatalog, crowds);
    }

    public PortalList(
            UserAttributePort users,
            TaskPortalDetail detail,
            Clock clock,
            TaskPortalPublishedCatalog publishedCatalog,
            TaskCrowdStore crowds) {
        this.users = users;
        this.detail = detail;
        this.clock = clock;
        this.publishedCatalog = publishedCatalog;
        this.crowds = crowds;
    }

    PageData<TaskCardView> list(Long userId, String category, Integer page, Integer pageSize) {
        if (userId != null && users == null) {
            return new PageData<>(0, List.of());
        }
        if (userId != null && detail.blacklisted(userId)) {
            return new PageData<>(0, List.of());
        }
        UserAttributes attrs = userId == null ? null : users.attributes(userId);
        Instant now = clock.instant();
        TaskPortalPublishedCatalog.PublishedBundle publishedBundle = publishedCatalog.publishedBundle();
        List<TaskDefinitionEntity> published = publishedBundle.definitions();
        Map<Long, SnapshotContent> snapshotByTask = publishedBundle.snapshots();
        Map<Long, TaskInstanceEntity> currentByTask =
                userId == null
                        ? Map.of()
                        : publishedCatalog.cachedCurrentInstancesForPublished(
                                userId, published, snapshotByTask, now);
        // One membership/resolver/evalContext per list request (was N× per published task).
        CrowdMembership membership = userId == null ? null : new StoreCrowdMembership(crowds);
        CrowdResolver resolver = userId == null ? null : new StoreCrowdResolver(crowds, users, userId);
        EvalContext filterCtx = userId == null ? null : EvalContexts.filter(attrs, now, resolver);
        // Top-K pending only (offset+pageSize) — avoid ~N PendingCard + full sort under ~1k catalog.
        PageQuery query = PageQuery.of(page, pageSize);
        long offset = query.offset();
        int limit = query.pageSize();
        int retain = (int) Math.min(Integer.MAX_VALUE - 8L, offset + (long) limit);
        if (retain < 1) {
            retain = 1;
        }
        Comparator<TaskPortalCardViews.PendingCard> asc =
                Comparator.comparingInt(TaskPortalCardViews.PendingCard::sortWeight)
                        .thenComparingLong(TaskPortalCardViews.PendingCard::taskId);
        PriorityQueue<TaskPortalCardViews.PendingCard> best =
                new PriorityQueue<>(Math.min(retain, 64), asc.reversed());
        long total = 0;
        for (TaskDefinitionEntity definition : published) {
            SnapshotContent snapshot = snapshotByTask.get(definition.getId());
            if (snapshot == null) {
                continue;
            }
            if (category != null && !category.isBlank() && !category.equals(snapshot.category())) {
                continue;
            }
            if (userId == null) {
                if (!VisibilityEvaluator.publicCard(
                        definition.getStatus(),
                        snapshot.startTime(),
                        snapshot.endTime(),
                        snapshot.gray(),
                        snapshot.filter(),
                        now)) {
                    continue;
                }
                total++;
                TaskPortalCardViews.considerPending(
                        best, retain, definition, snapshot, InstanceStatuses.NOT_STARTED);
                continue;
            }
            TaskInstanceEntity current = currentByTask.get(definition.getId());
            boolean inProgressThisCycle =
                    current != null && InstanceStatuses.IN_PROGRESS.equals(current.getStatus());
            // Boolean visible() — no VisibilityResult / reasons ArrayList per published task.
            boolean visible = VisibilityEvaluator.visible(
                    definition.getStatus(),
                    snapshot.startTime(),
                    snapshot.endTime(),
                    snapshot.gray(),
                    snapshot.filter(),
                    definition.getId(),
                    userId,
                    now,
                    membership,
                    filterCtx);
            if (!visible && !inProgressThisCycle) {
                continue;
            }
            String userStatus = current == null ? InstanceStatuses.NOT_STARTED : current.getStatus();
            total++;
            TaskPortalCardViews.considerPending(best, retain, definition, snapshot, userStatus);
        }
        List<TaskPortalCardViews.PendingCard> ranked = new ArrayList<>(best);
        ranked.sort(asc);
        int from = (int) Math.min(offset, ranked.size());
        int to = (int) Math.min(from + (long) limit, ranked.size());
        List<TaskCardView> pageCards = new ArrayList<>(Math.max(0, to - from));
        Map<Long, RewardPreviewView> rewards = publishedBundle.rewardPreviews();
        for (int i = from; i < to; i++) {
            TaskPortalCardViews.PendingCard row = ranked.get(i);
            pageCards.add(
                    TaskPortalCardViews.toCard(
                            row.definition(), row.snapshot(), row.userStatus(), rewards));
        }
        return new PageData<>(total, pageCards);
    }
}
