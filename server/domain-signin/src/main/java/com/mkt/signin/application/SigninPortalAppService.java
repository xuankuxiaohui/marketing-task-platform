package com.mkt.signin.application;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.convert.SigninSnapshotContent;
import com.mkt.signin.domain.ActivityStatuses;
import com.mkt.signin.domain.CalendarCellState;
import com.mkt.signin.domain.CalendarStates;
import com.mkt.signin.domain.ConsecutiveDays;
import com.mkt.signin.domain.SignSources;
import com.mkt.signin.domain.SigninDates;
import com.mkt.signin.entity.SgnActivityEntity;
import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import com.mkt.signin.entity.SgnRecordEntity;
import com.mkt.signin.response.CalendarDayView;
import com.mkt.signin.response.GrantFeedbackView;
import com.mkt.signin.response.PortalActivityView;
import com.mkt.signin.response.SigninActionResponse;
import com.mkt.signin.response.SigninCalendarResponse;
import com.mkt.signin.response.SigninTierView;
import com.mkt.signin.support.SigninErrorCodes;
import com.mkt.signin.support.SigninSettings;
import com.mkt.signin.support.SigninSourceIds;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigninPortalAppService {

    static final String CATCHUP_SOURCE_TYPE = "SIGNIN";

    private final SigninActivityStore activities;
    private final SigninSnapshotStore snapshots;
    private final SigninRecordStore records;
    private final RewardPort rewards;
    private final SigninSettings settings;
    private final Clock clock;

    public SigninPortalAppService(
            SigninActivityStore activities,
            SigninSnapshotStore snapshots,
            SigninRecordStore records,
            RewardPort rewards,
            SigninSettings settings,
            Clock clock) {
        this.activities = activities;
        this.snapshots = snapshots;
        this.records = records;
        this.rewards = rewards;
        this.settings = settings;
        this.clock = clock;
    }

    public List<PortalActivityView> listPublished(Instant now) {
        Instant at = now == null ? clock.instant() : now;
        List<PortalActivityView> out = new ArrayList<>();
        for (SgnActivityEntity entity : activities.listPublished()) {
            if (inWindow(entity, at)) {
                out.add(new PortalActivityView(
                        entity.getId(),
                        entity.getCode(),
                        entity.getName(),
                        SigninDates.toInstant(entity.getStartTime()),
                        SigninDates.toInstant(entity.getEndTime())));
            }
        }
        return out;
    }

    public SigninCalendarResponse calendar(long activityId, long userId, String yearMonth) {
        SgnActivityEntity activity = requirePublished(activityId);
        LocalDate today = SigninDates.today(clock);
        YearMonth month = yearMonth == null || yearMonth.isBlank()
                ? YearMonth.from(today)
                : parseYearMonth(yearMonth);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<SgnRecordEntity> monthRows = records.listInRange(activityId, userId, from, to);
        List<LocalDate> allDates = records.listSignDates(activityId, userId);
        int consecutive = ConsecutiveDays.compute(allDates, today);
        LocalDate activityStart = LocalDate.ofInstant(SigninDates.toInstant(activity.getStartTime()), SigninDates.UTC8);
        LocalDate activityEnd = LocalDate.ofInstant(SigninDates.toInstant(activity.getEndTime()), SigninDates.UTC8);
        List<CalendarStates.SignRecord> signed = monthRows.stream()
                .map(row -> new CalendarStates.SignRecord(row.getSignDate(), row.getSource()))
                .toList();
        List<CalendarDayView> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            CalendarCellState state = CalendarStates.of(
                    day, today, signed, settings.windowDays(), activityStart, activityEnd);
            days.add(new CalendarDayView(day, state.name()));
        }
        SigninSnapshotContent snap = liveSnapshot(activity);
        List<SigninTierView> tiers = tiersOf(snap);
        Integer nextDay = null;
        Long nextPrize = null;
        String hint = null;
        for (SigninTierView tier : tiers) {
            if (tier.day() > consecutive) {
                nextDay = tier.day();
                nextPrize = tier.prizeId();
                int remain = tier.day() - consecutive;
                hint = "再签 " + remain + " 天可得";
                break;
            }
        }
        return new SigninCalendarResponse(
                activity.getId(),
                activity.getCode(),
                activity.getName(),
                month.toString(),
                consecutive,
                settings.windowDays(),
                settings.dailyLimit(),
                settings.costPoints(),
                rewards.userSummary(userId).pointsBalance(),
                nextDay,
                nextPrize,
                hint,
                days,
                tiers);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SigninActionResponse checkin(long activityId, long userId) {
        LocalDate today = SigninDates.today(clock);
        return sign(activityId, userId, today, SignSources.CHECKIN, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SigninActionResponse catchup(long activityId, long userId, String signDateRaw) {
        LocalDate today = SigninDates.today(clock);
        LocalDate signDate = parseDate(signDateRaw);
        if (!signDate.isBefore(today)) {
            throw new BusinessException(SigninErrorCodes.CATCHUP_NOT_ALLOWED);
        }
        if (signDate.isBefore(today.minusDays(settings.windowDays()))) {
            throw new BusinessException(SigninErrorCodes.CATCHUP_WINDOW);
        }
        return sign(activityId, userId, signDate, SignSources.CATCHUP, true);
    }

    private SigninActionResponse sign(
            long activityId, long userId, LocalDate signDate, String source, boolean catchup) {
        SgnActivityEntity activity = requirePublished(activityId);
        Instant now = clock.instant();
        if (!inWindow(activity, now)) {
            throw new BusinessException(SigninErrorCodes.OUT_OF_WINDOW);
        }
        LocalDate activityStart = LocalDate.ofInstant(SigninDates.toInstant(activity.getStartTime()), SigninDates.UTC8);
        LocalDate activityEnd = LocalDate.ofInstant(SigninDates.toInstant(activity.getEndTime()), SigninDates.UTC8);
        if (signDate.isBefore(activityStart) || signDate.isAfter(activityEnd)) {
            throw new BusinessException(SigninErrorCodes.OUT_OF_WINDOW);
        }
        SgnRecordEntity existing = records.getByUk(activityId, userId, signDate);
        if (existing != null) {
            return alreadySigned(existing, activityId, userId);
        }
        if (catchup) {
            LocalDate today = SigninDates.today(clock);
            int used = records.countCatchupBetween(
                    activityId,
                    userId,
                    SigninDates.startOfDayUtc(today),
                    SigninDates.startOfDayUtc(today.plusDays(1)));
            if (used >= settings.dailyLimit()) {
                throw new BusinessException(SigninErrorCodes.CATCHUP_LIMIT);
            }
        }
        SgnActivitySnapshotEntity snapRow = requireLiveSnapshot(activity);
        SgnRecordEntity row = new SgnRecordEntity();
        row.setActivityId(activityId);
        row.setUserId(userId);
        row.setSignDate(signDate);
        row.setSource(source);
        row.setSnapshotId(snapRow.getId());
        row.setSimulated(0);
        row.setCreatedAt(SigninDates.toUtc(now));
        try {
            records.insert(row);
        } catch (DuplicateKeyException ex) {
            SgnRecordEntity winner = records.getByUk(activityId, userId, signDate);
            if (winner == null) {
                throw ex;
            }
            return alreadySigned(winner, activityId, userId);
        }
        if (catchup && settings.costPoints() > 0) {
            rewards.consume(
                    userId,
                    settings.costPoints(),
                    CATCHUP_SOURCE_TYPE,
                    SigninSourceIds.catchupConsume(activityId, userId, signDate),
                    "catchup");
        }
        List<GrantFeedbackView> grants = grantTiers(activityId, userId, snapRow);
        int consecutive = ConsecutiveDays.compute(records.listSignDates(activityId, userId), SigninDates.today(clock));
        return new SigninActionResponse(
                false, null, row.getId(), source, consecutive, grants);
    }

    private List<GrantFeedbackView> grantTiers(long activityId, long userId, SgnActivitySnapshotEntity snapRow) {
        SigninSnapshotContent content = JsonUtil.fromJson(snapRow.getContent(), SigninSnapshotContent.class);
        int consecutive = ConsecutiveDays.compute(records.listSignDates(activityId, userId), SigninDates.today(clock));
        List<GrantFeedbackView> out = new ArrayList<>();
        if (content.tiers() == null) {
            return out;
        }
        GrantContext ctx = GrantContext.defaults();
        for (SigninTierCommand tier : content.tiers()) {
            if (tier.day() == null || tier.prizeId() == null || tier.day() > consecutive) {
                continue;
            }
            GrantResult result = rewards.grant(
                    tier.prizeId(),
                    userId,
                    GrantSource.SIGNIN_DAY,
                    SigninSourceIds.grantTier(activityId, userId, tier.day()),
                    ctx);
            out.add(new GrantFeedbackView(
                    tier.day(), result.prizeId(), result.status().name(), result.hitIdempotent()));
        }
        return out;
    }

    private SigninActionResponse alreadySigned(SgnRecordEntity existing, long activityId, long userId) {
        int consecutive = ConsecutiveDays.compute(records.listSignDates(activityId, userId), SigninDates.today(clock));
        return new SigninActionResponse(
                true,
                SigninErrorCodes.DUPLICATE_DAY.message(),
                existing.getId(),
                existing.getSource(),
                consecutive,
                List.of());
    }

    private SgnActivityEntity requirePublished(long activityId) {
        SgnActivityEntity entity = activities.getById(activityId);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(SigninErrorCodes.NOT_FOUND);
        }
        if (!ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(SigninErrorCodes.NOT_PUBLISHED);
        }
        return entity;
    }

    private SgnActivitySnapshotEntity requireLiveSnapshot(SgnActivityEntity activity) {
        if (activity.versionOrZero() < 1) {
            throw new BusinessException(SigninErrorCodes.NOT_PUBLISHED);
        }
        SgnActivitySnapshotEntity snap = snapshots.getByActivityAndVersion(activity.getId(), activity.versionOrZero());
        if (snap == null) {
            throw new BusinessException(SigninErrorCodes.NOT_PUBLISHED);
        }
        return snap;
    }

    private SigninSnapshotContent liveSnapshot(SgnActivityEntity activity) {
        SgnActivitySnapshotEntity snap = requireLiveSnapshot(activity);
        return JsonUtil.fromJson(snap.getContent(), SigninSnapshotContent.class);
    }

    private static List<SigninTierView> tiersOf(SigninSnapshotContent snap) {
        if (snap.tiers() == null) {
            return List.of();
        }
        return snap.tiers().stream().map(tier -> new SigninTierView(tier.day(), tier.prizeId())).toList();
    }

    private static boolean inWindow(SgnActivityEntity entity, Instant now) {
        Instant start = SigninDates.toInstant(entity.getStartTime());
        Instant end = SigninDates.toInstant(entity.getEndTime());
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private static YearMonth parseYearMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, ex);
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, ex);
        }
    }
}
