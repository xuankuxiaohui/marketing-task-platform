package com.mkt.identity.application;

import com.mkt.identity.support.ForbiddenAuditSink;
import com.mkt.kernel.json.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdminForbiddenAudit implements ForbiddenAuditSink {

    private final IdentityAuditAppender audits;
    private final PlatformTransactionManager transactionManager;

    public AdminForbiddenAudit(IdentityAuditAppender audits, PlatformTransactionManager transactionManager) {
        this.audits = audits;
        this.transactionManager = transactionManager;
    }

    @Override
    public void onForbidden(
            long userId, String username, String method, String path, String ip, String userAgent) {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("method", method == null ? "" : method);
        summary.put("path", path == null ? "" : path);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> audits.append(
                "permission-denied",
                "sys_admin_user",
                String.valueOf(userId),
                userId,
                username,
                "FAILURE",
                ip,
                userAgent,
                JsonUtil.toJson(summary)));
    }
}
