package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Import entry extracted from {@link TaskCrowdAppService} (R22-1).
 * Move-only: importUsers semantics unchanged.
 * {@code require} and {@code evictCrowd} are copied so the moved body stays verbatim.
 */
@Component
public class CrowdImport {

    private final TaskCrowdStore store;
    private final UserAttributePort users;
    private final TaskSettings settings;
    private final PlatformCache cache;
    private final Clock clock;

    @Autowired
    public CrowdImport(
            TaskCrowdStore store,
            ObjectProvider<UserAttributePort> users,
            TaskSettings settings,
            ObjectProvider<PlatformCache> cache,
            Clock clock) {
        this(store, users.getIfAvailable(), settings, cache.getIfAvailable(), clock);
    }

    CrowdImport(
            TaskCrowdStore store,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock) {
        this.store = store;
        this.users = users;
        this.settings = settings;
        this.cache = cache;
        this.clock = clock;
    }

    CrowdImportResponse importUsers(long id, CrowdImportCommand command) {
        TaskCrowdEntity crowd = require(id);
        ParseResult parsed = parseUserIds(command == null ? null : command.content());
        int existing = crowd.getItemCount() == null ? 0 : crowd.getItemCount();
        int wouldAdd = 0;
        int alreadyInPack = 0;
        for (long userId : parsed.unique()) {
            if (store.countMember(id, userId) > 0) {
                alreadyInPack++;
            } else {
                wouldAdd++;
            }
        }
        if (existing + wouldAdd > settings.crowdMaxSize()) {
            throw new BusinessException(TaskErrorCodes.CROWD_SIZE_EXCEEDED);
        }
        int imported = 0;
        for (long userId : parsed.unique()) {
            if (store.countMember(id, userId) > 0) {
                continue;
            }
            imported += store.insertMemberIgnore(id, userId);
        }
        crowd.setItemCount(existing + imported);
        crowd.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(crowd);
        evictCrowd(id);
        return new CrowdImportResponse(imported, parsed.deduplicated() + alreadyInPack, parsed.invalid());
    }

    private ParseResult parseUserIds(String content) {
        if (content == null || content.isBlank()) {
            return new ParseResult(List.of(), 0, 0);
        }
        Set<Long> unique = new LinkedHashSet<>();
        int invalid = 0;
        int deduplicated = 0;
        for (String raw : content.split("\\r?\\n")) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Long userId = parseUserId(line);
            if (userId == null || !userExists(userId)) {
                invalid++;
                continue;
            }
            if (!unique.add(userId)) {
                deduplicated++;
            }
        }
        return new ParseResult(List.copyOf(unique), deduplicated, invalid);
    }

    private boolean userExists(long userId) {
        if (users == null) {
            return false;
        }
        UserAttributes attrs = users.attributes(userId);
        return attrs != null && attrs.accountStatus() != AccountStatus.NOT_FOUND;
    }

    private static Long parseUserId(String value) {
        if (value.length() > 19) {
            return null;
        }
        char first = value.charAt(0);
        if (first < '1' || first > '9') {
            return null;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            long id = Long.parseLong(value);
            return id > 0L ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private TaskCrowdEntity require(long id) {
        TaskCrowdEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private void evictCrowd(long crowdId) {
        if (cache != null) {
            cache.evictAfterCommit(CacheNamespace.TASK_CROWD, String.valueOf(crowdId));
        }
    }

    private record ParseResult(List<Long> unique, int deduplicated, int invalid) {}
}
