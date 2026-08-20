package com.mkt.identity.application;

import com.mkt.identity.command.CacheEvictCommand;
import com.mkt.identity.domain.CacheEvictLevels;
import com.mkt.identity.response.CacheEvictResponse;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.CacheNamespaceKind;
import com.mkt.infra.cache.CacheStatsView;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CacheAdminAppService {

    private static final String SESSION_NS = CacheNamespace.IDENTITY_SESSION.id();

    private final PlatformCache cache;
    private final IdentityAuditAppender audits;

    public CacheAdminAppService(PlatformCache cache, IdentityAuditAppender audits) {
        this.cache = cache;
        this.audits = audits;
    }

    public PageData<CacheStatsView> stats() {
        List<CacheStatsView> rows = new ArrayList<>();
        for (CacheNamespace ns : CacheNamespace.values()) {
            rows.add(cache.stats(ns));
        }
        return new PageData<>(rows.size(), rows);
    }

    @Transactional
    public CacheEvictResponse evict(CacheEvictCommand command) {
        String level = command.level() == null ? "" : command.level().trim();
        if (!CacheEvictLevels.valid(level)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "level 不合法");
        }
        String namespace = blankToNull(command.namespace());
        String prefix = blankToNull(command.prefix());
        String key = blankToNull(command.key());
        rejectExtra(level, namespace, prefix, key);
        rejectSession(namespace, prefix, key);
        CacheNamespace ns = resolveNamespace(namespace);
        CacheEvictResponse result;
        if (ns.kind() == CacheNamespaceKind.PLACEHOLDER) {
            result = new CacheEvictResponse(0, 0);
        } else if (CacheEvictLevels.KEY.equals(level)) {
            cache.evict(ns, key);
            result = new CacheEvictResponse(1, 1);
        } else if (CacheEvictLevels.PREFIX.equals(level)) {
            cache.evictPrefix(ns, prefix);
            result = new CacheEvictResponse(1, 1);
        } else {
            cache.evictNamespace(ns);
            result = new CacheEvictResponse(1, 1);
        }
        audits.append(
                "system",
                "cache-evict",
                "cache",
                ns.id(),
                "SUCCESS",
                summary(level, ns.id(), prefix, key, result));
        return result;
    }

    private static void rejectExtra(String level, String namespace, String prefix, String key) {
        if (CacheEvictLevels.KEY.equals(level)) {
            if (key == null || namespace == null || prefix != null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "KEY 须提供 namespace 与 key");
            }
            return;
        }
        if (CacheEvictLevels.PREFIX.equals(level)) {
            if (prefix == null || namespace == null || key != null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "PREFIX 须提供 namespace 与 prefix");
            }
            return;
        }
        if (namespace == null || prefix != null || key != null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "NAMESPACE 须只提供 namespace");
        }
    }

    private static void rejectSession(String namespace, String prefix, String key) {
        if (inSessionSpace(namespace) || inSessionSpace(prefix) || inSessionSpace(key)) {
            throw new BusinessException(CacheErrorCodes.SESSION_FORBIDDEN);
        }
    }

    private static boolean inSessionSpace(String value) {
        if (value == null) {
            return false;
        }
        return SESSION_NS.equals(value) || value.startsWith(SESSION_NS + ":");
    }

    private static CacheNamespace resolveNamespace(String namespace) {
        if (namespace == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return CacheNamespace.ofId(namespace)
                .orElseThrow(() -> new BusinessException(SystemErrorCodes.CACHE_NAMESPACE_UNKNOWN));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(
            String level, String namespace, String prefix, String key, CacheEvictResponse result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", level);
        body.put("namespace", namespace);
        if (prefix != null) {
            body.put("prefix", prefix);
        }
        if (key != null) {
            body.put("key", key);
        }
        body.put("evictedRedis", result.evictedRedis());
        body.put("notifiedInstances", result.notifiedInstances());
        return JsonUtil.toJson(body);
    }
}
