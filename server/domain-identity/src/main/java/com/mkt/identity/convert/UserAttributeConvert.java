package com.mkt.identity.convert;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.kernel.json.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;

public final class UserAttributeConvert {

    private static final TypeReference<List<String>> TAGS = new TypeReference<>() {};

    private UserAttributeConvert() {}

    public static UserAttributes from(PortalUserEntity entity) {
        if (entity == null) {
            return UserAttributes.notFound();
        }
        return new UserAttributes(
                blankToNull(entity.getProvince()),
                blankToNull(entity.getUserRole()),
                parseOrgId(entity.getOrgId()),
                parseUserLevel(entity.getUserLevel()),
                parseTags(entity.getTags()),
                IdentityTime.toInstant(entity.getRegisteredAt()),
                accountStatus(entity));
    }

    public static List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> tags = JsonUtil.fromJson(json, TAGS);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                out.add(tag);
            }
        }
        return List.copyOf(out);
    }

    static AccountStatus accountStatus(PortalUserEntity entity) {
        if (entity.deletedFlag()) {
            return AccountStatus.DELETED;
        }
        if (!entity.enabled()) {
            return AccountStatus.DISABLED;
        }
        return AccountStatus.ACTIVE;
    }

    static Long parseOrgId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Integer parseUserLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
