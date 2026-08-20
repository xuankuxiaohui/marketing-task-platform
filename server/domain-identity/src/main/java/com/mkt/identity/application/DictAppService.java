package com.mkt.identity.application;

import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictEntryUpdateCommand;
import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.convert.DictConvert;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.DictValues;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.DictEntryEntity;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.mapper.DictEntryMapper;
import com.mkt.identity.mapper.DictTypeMapper;
import com.mkt.identity.response.DictEntriesCache;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.identity.response.DictTypeView;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictAppService {

    private final DictTypeMapper types;
    private final DictEntryMapper entries;
    private final PlatformCache cache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public DictAppService(
            DictTypeMapper types,
            DictEntryMapper entries,
            PlatformCache cache,
            IdentityAuditAppender audits,
            Clock clock) {
        this.types = types;
        this.entries = entries;
        this.cache = cache;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<DictTypeView> pageTypes(PageQuery page) {
        long total = types.countAll();
        List<DictTypeEntity> rows = types.listPage(page.offset(), page.pageSize());
        List<DictTypeView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (DictTypeEntity row : rows) {
                views.add(DictConvert.toView(row));
            }
        }
        return new PageData<>(total, views);
    }

    public List<DictEntryOption> listEnabledEntries(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return List.of();
        }
        String code = typeCode.trim();
        DictEntriesCache snapshot = cache.get(CacheNamespace.DICT, code, DictEntriesCache.class, () -> loadEnabled(code));
        return snapshot == null ? List.of() : snapshot.items();
    }

    @Transactional
    public DictTypeEntity createType(DictTypeCreateCommand command) {
        String code = blankToNull(command.code());
        if (code == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典类型编码不能为空");
        }
        DictTypeEntity entity = new DictTypeEntity();
        entity.setCode(code);
        entity.setName(command.name().trim());
        entity.setStatus(RoleStatuses.ENABLED);
        entity.setRemark(blankToNull(command.remark()));
        LocalDateTime now = IdentityTime.toUtc(clock.instant());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            types.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(SystemErrorCodes.DICT_DUPLICATE_CODE, ex);
        }
        cache.evictAfterCommit(CacheNamespace.DICT, code);
        audits.append(
                "system",
                "dict-type-create",
                "sys_dict_type",
                String.valueOf(entity.getId()),
                "SUCCESS",
                summary("create", code, null, command.name()));
        return entity;
    }

    @Transactional
    public void updateType(long id, DictTypeUpdateCommand command) {
        DictTypeEntity existing = requireType(id);
        if (!RoleStatuses.valid(command.status())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典类型状态不合法");
        }
        String old = JsonUtil.toJson(
                Map.of("name", nullToEmpty(existing.getName()), "status", nullToEmpty(existing.getStatus())));
        existing.setName(command.name().trim());
        existing.setStatus(command.status());
        existing.setRemark(blankToNull(command.remark()));
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        types.updateById(existing);
        cache.evictAfterCommit(CacheNamespace.DICT, existing.getCode());
        String now = JsonUtil.toJson(Map.of("name", existing.getName(), "status", existing.getStatus()));
        audits.append(
                "system",
                "dict-type-update",
                "sys_dict_type",
                String.valueOf(id),
                "SUCCESS",
                summary("update", existing.getCode(), old, now));
    }

    @Transactional
    public void deleteType(long id) {
        DictTypeEntity existing = requireType(id);
        if (entries.countByTypeId(id) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典类型存在字典项，无法删除");
        }
        types.deleteById(id);
        cache.evictAfterCommit(CacheNamespace.DICT, existing.getCode());
        audits.append(
                "system",
                "dict-type-delete",
                "sys_dict_type",
                String.valueOf(id),
                "SUCCESS",
                summary("delete", existing.getCode(), existing.getName(), null));
    }

    @Transactional
    public DictEntryEntity createEntry(DictEntryCreateCommand command) {
        DictTypeEntity type = requireTypeByCode(command.typeCode());
        String value = DictValues.normalizeOrNull(command.value());
        if (value == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典项键值格式不正确");
        }
        DictEntryEntity entity = new DictEntryEntity();
        entity.setTypeId(type.getId());
        entity.setLabel(command.label().trim());
        entity.setValue(value);
        entity.setSort(command.sort());
        entity.setStatus(RoleStatuses.ENABLED);
        entity.setRemark(blankToNull(command.remark()));
        try {
            entries.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(SystemErrorCodes.DICT_ENTRY_DUPLICATE_VALUE, ex);
        }
        cache.evictAfterCommit(CacheNamespace.DICT, type.getCode());
        audits.append(
                "system",
                "dict-entry-create",
                "sys_dict_entry",
                String.valueOf(entity.getId()),
                "SUCCESS",
                summary("create", type.getCode(), null, value));
        return entity;
    }

    @Transactional
    public void updateEntry(long id, DictEntryUpdateCommand command) {
        DictEntryEntity existing = entries.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        if (!RoleStatuses.valid(command.status())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典项状态不合法");
        }
        String value = DictValues.normalizeOrNull(command.value());
        if (value == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典项键值格式不正确");
        }
        DictTypeEntity type = requireType(existing.getTypeId());
        String old = existing.getValue();
        existing.setLabel(command.label().trim());
        existing.setValue(value);
        existing.setSort(command.sort());
        existing.setStatus(command.status());
        existing.setRemark(blankToNull(command.remark()));
        try {
            entries.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(SystemErrorCodes.DICT_ENTRY_DUPLICATE_VALUE, ex);
        }
        cache.evictAfterCommit(CacheNamespace.DICT, type.getCode());
        audits.append(
                "system",
                "dict-entry-update",
                "sys_dict_entry",
                String.valueOf(id),
                "SUCCESS",
                summary("update", type.getCode(), old, value));
    }

    @Transactional
    public void deleteEntry(long id) {
        DictEntryEntity existing = entries.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        DictTypeEntity type = requireType(existing.getTypeId());
        entries.deleteById(id);
        cache.evictAfterCommit(CacheNamespace.DICT, type.getCode());
        audits.append(
                "system",
                "dict-entry-delete",
                "sys_dict_entry",
                String.valueOf(id),
                "SUCCESS",
                summary("delete", type.getCode(), existing.getValue(), null));
    }

    private DictEntriesCache loadEnabled(String typeCode) {
        DictTypeEntity type = types.getByCode(typeCode);
        if (type == null || !type.enabled()) {
            return new DictEntriesCache(List.of());
        }
        List<DictEntryEntity> rows = entries.listEnabledByTypeId(type.getId());
        List<DictEntryOption> options = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (DictEntryEntity row : rows) {
                options.add(DictConvert.toOption(row));
            }
        }
        return new DictEntriesCache(options);
    }

    private DictTypeEntity requireType(long id) {
        DictTypeEntity existing = types.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private DictTypeEntity requireTypeByCode(String raw) {
        String code = blankToNull(raw);
        if (code == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "字典类型编码不能为空");
        }
        DictTypeEntity type = types.getByCode(code);
        if (type == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return type;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String summary(String action, String typeCode, String oldValue, String newValue) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("typeCode", typeCode);
        if (oldValue != null) {
            body.put("old", oldValue);
        }
        if (newValue != null) {
            body.put("new", newValue);
        }
        return JsonUtil.toJson(body);
    }
}
