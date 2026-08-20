package com.mkt.identity.application;

import com.mkt.identity.command.InternalAppCreateCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.convert.InternalAppConvert;
import com.mkt.identity.domain.InternalAppSecrets;
import com.mkt.identity.domain.UserStatuses;
import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.response.InternalAppCreatedResponse;
import com.mkt.identity.response.InternalAppRotateResponse;
import com.mkt.identity.response.InternalAppView;
import com.mkt.identity.support.InternalAppErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalAppAppService {

    private static final int APP_ID_ATTEMPTS = 3;

    private final InternalAppMapper apps;
    private final InternalAppSecretCipher cipher;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public InternalAppAppService(
            InternalAppMapper apps,
            InternalAppSecretCipher cipher,
            IdentityAuditAppender audits,
            Clock clock) {
        this.apps = apps;
        this.cipher = cipher;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<InternalAppView> page(PageQuery page) {
        long total = apps.countAll();
        List<InternalAppEntity> rows = apps.listPage(page.offset(), page.pageSize());
        List<InternalAppView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (InternalAppEntity row : rows) {
                views.add(InternalAppConvert.toView(row));
            }
        }
        return new PageData<>(total, views);
    }

    @Transactional
    public InternalAppCreatedResponse create(InternalAppCreateCommand command) {
        String secret = InternalAppSecrets.randomSecret(cipher.random());
        InternalAppEntity entity = new InternalAppEntity();
        entity.setAppName(command.appName().trim());
        entity.setSecretCipher(cipher.encrypt(secret));
        entity.setStatus(UserStatuses.ENABLED);
        LocalDateTime now = IdentityTime.toUtc(clock.instant());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        insertWithAppId(entity);
        audits.append(
                "internal-app-add",
                "sys_internal_app",
                String.valueOf(entity.getId()),
                "SUCCESS",
                summary("add", entity.getAppId()));
        return new InternalAppCreatedResponse(entity.getId(), entity.getAppId(), secret);
    }

    @Transactional
    public InternalAppRotateResponse rotate(long id) {
        InternalAppEntity existing = require(id);
        String secret = InternalAppSecrets.randomSecret(cipher.random());
        Instant prevExpireAt = clock.instant().plus(Duration.ofHours(InternalAppSecrets.ROTATE_WINDOW_HOURS));
        existing.setPrevSecretCipher(existing.getSecretCipher());
        existing.setPrevExpireAt(IdentityTime.toUtc(prevExpireAt));
        existing.setSecretCipher(cipher.encrypt(secret));
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        apps.updateById(existing);
        audits.append(
                "internal-app-rotate",
                "sys_internal_app",
                String.valueOf(id),
                "SUCCESS",
                summary("rotate", existing.getAppId()));
        return new InternalAppRotateResponse(secret, prevExpireAt);
    }

    @Transactional
    public void disable(long id) {
        setStatus(id, UserStatuses.DISABLED, "disable");
    }

    @Transactional
    public void enable(long id) {
        setStatus(id, UserStatuses.ENABLED, "enable");
    }

    private void setStatus(long id, String status, String action) {
        InternalAppEntity existing = require(id);
        existing.setStatus(status);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        apps.updateById(existing);
        audits.append(
                "internal-app-" + action,
                "sys_internal_app",
                String.valueOf(id),
                "SUCCESS",
                summary(action, existing.getAppId()));
    }

    private InternalAppEntity require(long id) {
        InternalAppEntity existing = apps.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private void insertWithAppId(InternalAppEntity entity) {
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < APP_ID_ATTEMPTS; attempt++) {
            entity.setAppId(InternalAppSecrets.randomAppId(cipher.random()));
            try {
                apps.insert(entity);
                return;
            } catch (DuplicateKeyException ex) {
                last = ex;
            }
        }
        throw new BusinessException(InternalAppErrorCodes.DUPLICATE, last);
    }

    private static String summary(String action, String appId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("appId", appId);
        return JsonUtil.toJson(body);
    }
}
