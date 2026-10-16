package com.mkt.identity.application;

import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictEntryUpdateCommand;
import com.mkt.identity.domain.DictValues;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.DictEntryEntity;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.mapper.DictEntryMapper;
import com.mkt.identity.mapper.DictTypeMapper;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Create and update dictionary entries extracted from {@link DictAppService} (R34-2).
 * Move-only: createEntry / updateEntry semantics unchanged. {@code requireType},
 * {@code requireTypeByCode}, {@code blankToNull}, and {@code summary} are save-side copies;
 * the facade keeps {@code requireType} and {@code summary} for deleteType and deleteEntry.
 * {@code requireTypeByCode} and {@code blankToNull} move with createEntry / updateEntry.
 */
@Component
public class DictEntrySave {

    private final DictTypeMapper types;
    private final DictEntryMapper entries;
    private final PlatformCache cache;
    private final IdentityAuditAppender audits;

    @Autowired
    public DictEntrySave(
            DictTypeMapper types, DictEntryMapper entries, PlatformCache cache, IdentityAuditAppender audits) {
        this.types = types;
        this.entries = entries;
        this.cache = cache;
        this.audits = audits;
    }

    DictEntryEntity createEntry(DictEntryCreateCommand command) {
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

    void updateEntry(long id, DictEntryUpdateCommand command) {
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
