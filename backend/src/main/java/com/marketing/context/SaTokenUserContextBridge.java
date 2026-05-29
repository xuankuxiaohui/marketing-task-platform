package com.marketing.context;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.marketing.task.domain.enums.Platform;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class SaTokenUserContextBridge {

    private SaTokenUserContextBridge() {
    }

    /**
     * Build UserContext for a client (C-end) user from the client StpLogic.
     */
    public static UserContext buildClientUserContext(StpLogic clientStpLogic) {
        String userId = clientStpLogic.getLoginIdAsString();
        return UserContext.builder()
                .userId(userId)
                .province(getExtraString(clientStpLogic, "province"))
                .role(getExtraString(clientStpLogic, "role"))
                .tags(parseTagsSet(clientStpLogic, "tags"))
                .orgId(getExtraString(clientStpLogic, "orgId"))
                .level(getExtraInt(clientStpLogic, "level"))
                .platform(Platform.WEB)
                .build();
    }

    /**
     * Build UserContext for an admin user from StpUtil (default admin StpLogic).
     */
    public static UserContext buildAdminUserContext() {
        String userId = StpUtil.getLoginIdAsString();
        return UserContext.builder()
                .userId(userId)
                .platform(Platform.ADMIN)
                .build();
    }

    private static String getExtraString(StpLogic stpLogic, String key) {
        try {
            Object val = stpLogic.getExtra(key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getExtraInt(StpLogic stpLogic, String key) {
        try {
            Object val = stpLogic.getExtra(key);
            if (val == null) return null;
            if (val instanceof Integer i) return i;
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> parseTagsSet(StpLogic stpLogic, String key) {
        try {
            Object val = stpLogic.getExtra(key);
            if (val == null) return Set.of();
            String str = val.toString();
            if (str.isBlank()) return Set.of();
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }
}
