package com.marketing.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.common.EventType;
import com.marketing.task.domain.entity.EventLog;
import com.marketing.task.mapper.EventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTrackingService {
    private final EventLogMapper eventLogMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void track(EventType type, Long taskId, Long instanceId,
                      Long stepId, String userId, String platform,
                      Map<String, Object> eventData) {
        try {
            EventLog log = new EventLog();
            log.setEventType(type.name());
            log.setTaskId(taskId);
            log.setInstanceId(instanceId);
            log.setStepId(stepId);
            log.setUserId(userId);
            log.setPlatform(platform);
            log.setEventData(eventData != null ? objectMapper.writeValueAsString(eventData) : null);
            eventLogMapper.insert(log);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event data: type={}, taskId={}", type, taskId, e);
        } catch (Exception e) {
            log.warn("Failed to persist event: type={}, taskId={}", type, taskId, e);
        }
    }
}
