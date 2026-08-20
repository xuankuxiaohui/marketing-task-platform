package com.mkt.identity.config;

import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.identity.command.ConfigCreateCommand;
import com.mkt.identity.command.ConfigUpdateCommand;
import com.mkt.identity.convert.ConfigConvert;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.ConfigMasks;
import com.mkt.identity.domain.ConfigValueTypes;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.identity.query.ConfigQuery;
import com.mkt.identity.response.ConfigView;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
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
import tools.jackson.databind.JsonNode;

@Service
public class ConfigAppService {

    private final SysConfigMapper configs;
    private final PlatformCache cache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public ConfigAppService(
            SysConfigMapper configs, PlatformCache cache, IdentityAuditAppender audits, Clock clock) {
        this.configs = configs;
        this.cache = cache;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<ConfigView> page(ConfigQuery query) {
        String group = blankToNull(query.configGroup());
        String key = blankToNull(query.key());
        long total = configs.countPage(group, key);
        List<SysConfigEntity> rows = configs.listPage(group, key, query.page().offset(), query.page().pageSize());
        List<ConfigView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (SysConfigEntity row : rows) {
                views.add(ConfigConvert.toView(row));
            }
        }
        return new PageData<>(total, views);
    }

    @Transactional
    public SysConfigEntity create(ConfigCreateCommand command) {
        String valueType = command.valueType().trim();
        if (!ConfigValueTypes.valid(valueType)) {
            throw new BusinessException(SystemErrorCodes.CONFIG_TYPE_MISMATCH);
        }
        if (!ConfigValueTypes.matches(valueType, command.configValue())) {
            throw new BusinessException(SystemErrorCodes.CONFIG_TYPE_MISMATCH);
        }
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(command.configKey().trim());
        entity.setConfigGroup(command.configGroup().trim());
        entity.setConfigValue(command.configValue());
        entity.setValueType(valueType);
        entity.setMasked(Boolean.TRUE.equals(command.masked()) ? 1 : 0);
        entity.setStatus(RoleStatuses.ENABLED);
        entity.setRemark(blankToNull(command.remark()));
        LocalDateTime now = IdentityTime.toUtc(clock.instant());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            configs.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "配置键已存在", ex);
        }
        cache.evictAfterCommit(CacheNamespace.CONFIG, entity.getConfigKey());
        audits.append(
                "system",
                "config-create",
                "sys_config",
                entity.getConfigKey(),
                "SUCCESS",
                summary("create", entity, null, displayValue(entity)));
        return entity;
    }

    @Transactional
    public void update(String key, ConfigUpdateCommand command) {
        SysConfigEntity existing = configs.getByKey(key);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        String oldDisplay = displayValue(existing);
        if (command.configGroup() != null && !command.configGroup().isBlank()) {
            existing.setConfigGroup(command.configGroup().trim());
        }
        if (command.status() != null) {
            if (!RoleStatuses.valid(command.status())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "配置状态不合法");
            }
            existing.setStatus(command.status());
        }
        if (command.remark() != null) {
            existing.setRemark(blankToNull(command.remark()));
        }
        if (command.masked() != null) {
            existing.setMasked(Boolean.TRUE.equals(command.masked()) ? 1 : 0);
        }
        String valueType = existing.getValueType();
        if (command.valueType() != null && !command.valueType().isBlank()) {
            if (!ConfigValueTypes.valid(command.valueType().trim())) {
                throw new BusinessException(SystemErrorCodes.CONFIG_TYPE_MISMATCH);
            }
            valueType = command.valueType().trim();
            existing.setValueType(valueType);
        }
        JsonNode valueNode = command.value();
        if (valueNode != null) {
            if (valueNode.isNull() || valueNode.isMissingNode()) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "配置值不能为空");
            }
            String raw = rawValue(valueNode);
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "配置值不能为空");
            }
            boolean maskSentinel =
                    existing.maskedFlag() && ConfigMasks.DISPLAY.equals(raw);
            if (!maskSentinel) {
                if (!ConfigValueTypes.matches(valueType, raw)) {
                    throw new BusinessException(SystemErrorCodes.CONFIG_TYPE_MISMATCH);
                }
                existing.setConfigValue(raw);
            }
        }
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        configs.updateById(existing);
        cache.evictAfterCommit(CacheNamespace.CONFIG, existing.getConfigKey());
        audits.append(
                "system",
                "config-update",
                "sys_config",
                existing.getConfigKey(),
                "SUCCESS",
                summary("update", existing, oldDisplay, displayValue(existing)));
    }

    private static String rawValue(JsonNode node) {
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asString();
        }
        return JsonUtil.toJson(node);
    }

    private static String displayValue(SysConfigEntity entity) {
        return entity.maskedFlag() ? ConfigMasks.DISPLAY : entity.getConfigValue();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(String action, SysConfigEntity entity, String oldValue, String newValue) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("configKey", entity.getConfigKey());
        if (oldValue != null) {
            body.put("old", oldValue);
        }
        if (newValue != null) {
            body.put("new", newValue);
        }
        return JsonUtil.toJson(body);
    }
}
