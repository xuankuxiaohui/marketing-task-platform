package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCrowdAppService {

    private final TaskCrowdStore store;
    private final TaskDefinitionStore definitions;
    private final UserAttributePort users;
    private final TaskSettings settings;
    private final PlatformCache cache;
    private final Clock clock;

    @Autowired
    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            ObjectProvider<UserAttributePort> users,
            TaskSettings settings,
            ObjectProvider<PlatformCache> cache,
            Clock clock) {
        this(store, definitions, users.getIfAvailable(), settings, cache.getIfAvailable(), clock);
    }

    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock) {
        this.store = store;
        this.definitions = definitions;
        this.users = users;
        this.settings = settings;
        this.cache = cache;
        this.clock = clock;
    }

    public CrowdResponse get(long id) {
        return toResponse(require(id));
    }

    public PageData<CrowdResponse> page(PageQuery page) {
        long total = store.countAll();
        List<TaskCrowdEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(TaskCrowdAppService::toResponse).toList());
    }

    @Transactional
    public CrowdResponse create(CrowdSaveCommand command) {
        CrowdSaveCommand normalized = requireValid(command);
        if (store.getByCode(normalized.code()) != null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包编码已存在");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TaskCrowdEntity entity = new TaskCrowdEntity();
        entity.setCode(normalized.code());
        entity.setName(normalized.name());
        entity.setStatus(normalized.status());
        entity.setItemCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            store.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包编码已存在", ex);
        }
        return toResponse(entity);
    }

    @Transactional
    public CrowdResponse update(long id, CrowdSaveCommand command) {
        TaskCrowdEntity existing = require(id);
        CrowdSaveCommand normalized = requireValid(command);
        if (!existing.getCode().equals(normalized.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        existing.setName(normalized.name());
        existing.setStatus(normalized.status());
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        evictCrowd(id);
        return toResponse(existing);
    }

    @Transactional
    public void delete(long id) {
        require(id);
        if (definitions.countReferencingCrowd(id) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包仍被任务引用");
        }
        store.deleteById(id);
        evictCrowd(id);
    }

    @Transactional
    public CrowdImportResponse importUsers(long id, CrowdImportCommand command) {
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

    private CrowdSaveCommand requireValid(CrowdSaveCommand command) {
        if (command == null || !TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        String status = command.status() == null || command.status().isBlank()
                ? CrowdStatuses.ENABLED
                : command.status().trim().toUpperCase(Locale.ROOT);
        if (!CrowdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 仅允许 ENABLED|DISABLED");
        }
        return new CrowdSaveCommand(command.code().trim(), command.name().trim(), status);
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

    private static CrowdResponse toResponse(TaskCrowdEntity entity) {
        return new CrowdResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getItemCount() == null ? 0 : entity.getItemCount(),
                entity.getStatus(),
                TaskTime.toInstant(entity.getUpdatedAt()));
    }

    private record ParseResult(List<Long> unique, int deduplicated, int invalid) {}
}
