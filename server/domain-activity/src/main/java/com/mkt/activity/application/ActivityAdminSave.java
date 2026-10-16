package com.mkt.activity.application;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityDraftContent;
import com.mkt.activity.domain.ActivityCodes;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Save entry extracted from {@link ActivityAdminAppService} (R29-1).
 * Move-only: save semantics unchanged.
 */
@Component
public class ActivityAdminSave {

    private final ActivityAdminNormalize normalize;
    private final RewardPort rewards;
    private final Clock clock;
    private final ActivityAdminPersist persist;

    @Autowired
    public ActivityAdminSave(
            ActivityAdminNormalize normalize,
            RewardPort rewards,
            Clock clock,
            ActivityAdminPersist persist) {
        this.normalize = normalize;
        this.rewards = rewards;
        this.clock = clock;
        this.persist = persist;
    }

    ActivitySaveResponse save(ActivitySaveCommand command) {
        String html = normalize.sanitizeHtml(command.richText());
        ActivityGrayCommand gray = normalize.normalizeGray(command.gray());
        List<ActivitySubmoduleCommand> submodules = normalize.normalizeSubmodules(command.submodules());
        if (!ActivityCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "活动编码格式不正确");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(ActivityErrorCodes.TIME_WINDOW_INVALID);
        }
        int newUserDays = normalize.normalizeNewUserDays(command.newUserDays());
        ActivityAdminNormalize.validateLimits(
                command.userDailyLimit(), command.userTotalLimit(), command.globalDailyLimit());
        if (command.participationPrizeId() != null && !rewards.prizeEnabled(command.participationPrizeId())) {
            throw new BusinessException(ActivityErrorCodes.PRIZE_INVALID);
        }
        LocalDateTime now = ActivityDates.toUtc(clock.instant());
        ActivityDraftContent draft = new ActivityDraftContent(
                command.name(),
                command.startTime(),
                command.endTime(),
                html,
                gray,
                submodules,
                command.participationPrizeId(),
                command.allowUserIds() == null ? List.of() : List.copyOf(command.allowUserIds()),
                command.allowCrowdCodes() == null ? List.of() : List.copyOf(command.allowCrowdCodes()),
                Boolean.TRUE.equals(command.newUserOnly()),
                newUserDays,
                command.userDailyLimit(),
                command.userTotalLimit(),
                command.globalDailyLimit(),
                command.regions() == null ? List.of() : List.copyOf(command.regions()),
                null);
        if (command.id() == null) {
            return persist.insertNew(command, draft, now);
        }
        return persist.updateExisting(command, draft, now);
    }
}
