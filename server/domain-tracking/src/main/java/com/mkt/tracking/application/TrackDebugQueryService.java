package com.mkt.tracking.application;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.tracking.convert.TrackDebugConvert;
import com.mkt.tracking.convert.TrackTime;
import com.mkt.tracking.query.EventLogQuery;
import com.mkt.tracking.query.TrackDebugQuery;
import com.mkt.tracking.response.TrackDebugEventResponse;
import com.mkt.tracking.support.TrackErrorCodes;
import com.mkt.tracking.support.TrackOperator;
import com.mkt.tracking.support.TrackSettings;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TrackDebugQueryService {

    static final String RATE_LIMIT_KEY_PREFIX = "track-query:";

    private final EventLogStore eventLogStore;
    private final SlidingWindowRateLimiter rateLimiter;
    private final TrackSettings settings;

    public TrackDebugQueryService(
            EventLogStore eventLogStore, SlidingWindowRateLimiter rateLimiter, TrackSettings settings) {
        this.eventLogStore = eventLogStore;
        this.rateLimiter = rateLimiter;
        this.settings = settings;
    }

    public PageData<TrackDebugEventResponse> query(TrackDebugQuery query) {
        long operatorId = TrackOperator.requireUserId();
        if (!rateLimiter.tryAcquire(
                RateLimitDim.USER,
                RATE_LIMIT_KEY_PREFIX + operatorId,
                60,
                settings.queryRateLimitPerMinute())) {
            throw new BusinessException(TrackErrorCodes.QUERY_RATE_LIMITED);
        }
        EventLogQuery criteria = toCriteria(query);
        long total = eventLogStore.countByQuery(criteria);
        List<TrackDebugEventResponse> records = eventLogStore.listByQuery(criteria).stream()
                .map(TrackDebugConvert::toResponse)
                .toList();
        return new PageData<>(total, records);
    }

    private EventLogQuery toCriteria(TrackDebugQuery query) {
        TrackDebugQuery q = query == null
                ? new TrackDebugQuery(null, null, null, null, null, null, null)
                : query;
        return new EventLogQuery(
                blankToNull(q.eventCode()),
                q.userId(),
                normalizeSource(q.source()),
                blankToNull(q.deviceId()),
                TrackTime.toUtc(q.from()),
                TrackTime.toUtc(q.to()),
                settings.querySampleRatioPercent(),
                q.page().offset(),
                q.page().pageSize());
    }

    private static String normalizeSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!"CLIENT".equals(normalized) && !"SERVER".equals(normalized)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "source 仅允许 CLIENT|SERVER");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
