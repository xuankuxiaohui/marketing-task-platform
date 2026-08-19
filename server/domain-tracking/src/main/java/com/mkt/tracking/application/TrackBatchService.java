package com.mkt.tracking.application;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.tracking.command.TrackBatchCommand;
import com.mkt.tracking.command.TrackEventCommand;
import com.mkt.tracking.command.TrackIdentity;
import com.mkt.tracking.domain.AcceptedEvent;
import com.mkt.tracking.domain.EventFilterDecision;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.TrackPolicies;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.response.TrackBatchResponse;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackErrorCodes;
import com.mkt.tracking.support.TrackSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client batch ingest: one {@code evt_event_log} row per request (R28.9). */
public class TrackBatchService {

    static final String SOURCE_CLIENT = "CLIENT";

    private final EventLogStore eventLogStore;
    private final EventMetadataStore metadataStore;
    private final SlidingWindowRateLimiter rateLimiter;
    private final TrackSettings settings;
    private final TrackDropCounters drops;
    private final Clock clock;

    public TrackBatchService(
            EventLogStore eventLogStore,
            EventMetadataStore metadataStore,
            SlidingWindowRateLimiter rateLimiter,
            TrackSettings settings,
            TrackDropCounters drops,
            Clock clock) {
        this.eventLogStore = eventLogStore;
        this.metadataStore = metadataStore;
        this.rateLimiter = rateLimiter;
        this.settings = settings;
        this.drops = drops;
        this.clock = clock;
    }

    public TrackBatchResponse ingest(TrackBatchCommand command, TrackIdentity identity) {
        if (command == null || command.events() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (command.events().size() > settings.batchMaxSize()) {
            throw new BusinessException(TrackErrorCodes.BATCH_OVERFLOW);
        }
        if (!rateLimiter.tryAcquire(rateLimitDim(identity), rateLimitKey(identity), 60, settings.rateLimitPerMinute())) {
            throw new BusinessException(TrackErrorCodes.BATCH_RATE_LIMITED);
        }

        List<AcceptedEvent> accepted = new ArrayList<>();
        int malformed = 0;
        int unregistered = 0;
        int disabled = 0;
        for (TrackEventCommand event : command.events()) {
            if (event == null) {
                malformed++;
                continue;
            }
            EventFilterDecision decision = TrackPolicies.decide(
                    event.code(),
                    event.props(),
                    settings.eventMaxPayloadBytes(),
                    metadataStore.statusOf(event.code()),
                    settings.unregisteredPolicy(),
                    settings.disabledEventPolicy());
            switch (decision) {
                case DROP_MALFORMED -> malformed++;
                case DROP_UNREGISTERED -> unregistered++;
                case DROP_DISABLED -> disabled++;
                case ACCEPT -> accepted.add(toAccepted(event));
            }
        }
        drops.addMalformed(malformed);
        drops.addUnregistered(unregistered);
        drops.addDisabled(disabled);
        int dropped = malformed + unregistered + disabled;
        if (!accepted.isEmpty()) {
            eventLogStore.insert(toRow(command, identity, accepted));
        }
        return new TrackBatchResponse(accepted.size(), dropped);
    }

    private AcceptedEvent toAccepted(TrackEventCommand event) {
        MetadataStatus status = metadataStore.statusOf(event.code());
        boolean registered = status != MetadataStatus.MISSING;
        return new AcceptedEvent(
                event.code(), TrackPolicies.normalizeProps(event.props()), event.clientTime(), registered);
    }

    private EvtEventLogEntity toRow(TrackBatchCommand command, TrackIdentity identity, List<AcceptedEvent> accepted) {
        List<Map<String, Object>> items = new ArrayList<>(accepted.size());
        boolean allRegistered = true;
        for (AcceptedEvent event : accepted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", event.code());
            item.put("props", event.props());
            item.put("clientTime", event.clientTime());
            items.add(item);
            if (!event.registered()) {
                allRegistered = false;
            }
        }
        EvtEventLogEntity row = new EvtEventLogEntity();
        row.setSource(SOURCE_CLIENT);
        row.setEventCode(accepted.get(0).code());
        row.setUserId(identity == null ? null : identity.userId());
        row.setDeviceId(clip(identity == null ? null : identity.deviceId(), 36));
        row.setPlatform(clip(command.platform(), 16));
        row.setAppVersion(clip(command.appVersion(), 32));
        row.setIp(clip(identity == null ? null : identity.ip(), 45));
        row.setEvents(JsonUtil.toJson(items));
        row.setBatchSize(accepted.size());
        row.setRegistered(allRegistered ? 1 : 0);
        row.setSimulated(0);
        row.setServerTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return row;
    }

    private static RateLimitDim rateLimitDim(TrackIdentity identity) {
        if (identity != null && identity.userId() != null) {
            return RateLimitDim.USER;
        }
        return RateLimitDim.CUSTOM;
    }

    private static String rateLimitKey(TrackIdentity identity) {
        if (identity != null && identity.userId() != null) {
            return "track:" + identity.userId();
        }
        if (identity != null && identity.deviceId() != null && !identity.deviceId().isBlank()) {
            return "track:" + identity.deviceId();
        }
        if (identity != null && identity.ip() != null && !identity.ip().isBlank()) {
            return "track-ip:" + identity.ip();
        }
        return "track:anonymous";
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
