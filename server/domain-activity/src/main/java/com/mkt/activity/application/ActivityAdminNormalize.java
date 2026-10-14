package com.mkt.activity.application;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.domain.ActivityHtmlSanitizer;
import com.mkt.activity.domain.GrayTypes;
import com.mkt.activity.domain.SubmoduleTypes;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.kernel.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Normalize helpers extracted from {@link ActivityAdminAppService} (R3-1).
 * Move-only: sanitizeHtml / normalizeGray / normalizeSubmodules /
 * normalizeNewUserDays / validateLimits / invalidLimit semantics unchanged.
 */
@Component
public class ActivityAdminNormalize {

    private final ActivitySettings settings;

    @Autowired
    public ActivityAdminNormalize(ActivitySettings settings) {
        this.settings = settings;
    }

    String sanitizeHtml(String raw) {
        String html = ActivityHtmlSanitizer.sanitize(raw);
        if (html.isBlank()) {
            throw new BusinessException(ActivityErrorCodes.HTML_EMPTY);
        }
        if (html.length() > ActivityHtmlSanitizer.MAX_LENGTH) {
            throw new BusinessException(ActivityErrorCodes.HTML_TOO_LONG);
        }
        return html;
    }

    ActivityGrayCommand normalizeGray(ActivityGrayCommand gray) {
        String type = gray == null || gray.type() == null || gray.type().isBlank()
                ? GrayTypes.NONE
                : gray.type().trim().toUpperCase(Locale.ROOT);
        if (!GrayTypes.valid(type)) {
            throw new BusinessException(ActivityErrorCodes.GRAY_INVALID);
        }
        Integer ratio = gray == null ? null : gray.ratio();
        if (GrayTypes.RATIO.equals(type)) {
            if (ratio == null || ratio < 0 || ratio > 100) {
                throw new BusinessException(ActivityErrorCodes.GRAY_INVALID);
            }
        } else {
            ratio = null;
        }
        return new ActivityGrayCommand(type, ratio);
    }

    List<ActivitySubmoduleCommand> normalizeSubmodules(List<ActivitySubmoduleCommand> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ActivitySubmoduleCommand> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparingInt(row -> row.sort() == null ? 0 : row.sort()));
        Set<String> keys = new HashSet<>();
        List<ActivitySubmoduleCommand> out = new ArrayList<>();
        int i = 0;
        for (ActivitySubmoduleCommand row : sorted) {
            if (row.type() == null || !SubmoduleTypes.valid(row.type()) || row.refId() == null || row.refId() < 1) {
                throw new BusinessException(ActivityErrorCodes.SUBMODULE_INVALID);
            }
            String key = row.type() + ":" + row.refId();
            if (!keys.add(key)) {
                throw new BusinessException(ActivityErrorCodes.SUBMODULE_INVALID);
            }
            int sort = row.sort() == null ? i : row.sort();
            out.add(new ActivitySubmoduleCommand(row.type(), row.refId(), sort));
            i++;
        }
        return List.copyOf(out);
    }

    int normalizeNewUserDays(Integer value) {
        int days = value == null ? settings.newUserDays() : value;
        if (days < 1 || days > 365) {
            throw new BusinessException(ActivityErrorCodes.RULE_INVALID);
        }
        return days;
    }

    static void validateLimits(Integer daily, Integer total, Integer global) {
        if (invalidLimit(daily) || invalidLimit(total) || invalidLimit(global)) {
            throw new BusinessException(ActivityErrorCodes.RULE_INVALID);
        }
    }

    private static boolean invalidLimit(Integer value) {
        return value != null && value < 0;
    }
}
