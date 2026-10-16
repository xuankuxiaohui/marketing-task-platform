package com.mkt.identity.application;

import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.mapper.DictTypeMapper;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Create and update type entries extracted from {@link DictAppService} (R34-1).
 * Move-only: createType / updateType semantics unchanged. {@code requireType}, {@code blankToNull},
 * and {@code summary} are save-side copies; the facade keeps those helpers for deleteType and entry
 * methods. {@code nullToEmpty} is only used by updateType.
 */
@Component
public class DictTypeSave {

    private final DictTypeMapper types;
    private final PlatformCache cache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    @Autowired
    public DictTypeSave(DictTypeMapper types, PlatformCache cache, IdentityAuditAppender audits, Clock clock) {
        this.types = types;
        this.cache = cache;
        this.audits = audits;
        this.clock = clock;
    }

    DictTypeEntity createType(DictTypeCreateCommand command) {
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

    void updateType(long id, DictTypeUpdateCommand command) {
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

    private DictTypeEntity requireType(long id) {
        DictTypeEntity existing = types.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
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
