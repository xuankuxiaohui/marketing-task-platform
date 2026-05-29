package com.marketing.task.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.common.EventType;
import com.marketing.system.domain.entity.EventLog;
import com.marketing.system.mapper.EventLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventTrackingServiceTest {

    @Mock
    private EventLogMapper eventLogMapper;

    private EventTrackingService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, EventLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new EventTrackingService(eventLogMapper);
    }

    @Test
    void shouldInsertEventLogOnTrack() {
        service.track(EventType.TASK_VIEWED, 1L, null, null, "user1", "IOS", Map.of("k", "v"));

        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogMapper).insert(captor.capture());
        EventLog log = captor.getValue();
        assertEquals("TASK_VIEWED", log.getEventType());
        assertEquals(1L, log.getTaskId());
        assertEquals("user1", log.getUserId());
        assertEquals("IOS", log.getPlatform());
        assertNotNull(log.getEventData());
    }

    @Test
    void shouldNotThrowOnMapperException() {
        doThrow(new RuntimeException("DB error")).when(eventLogMapper).insert(any(EventLog.class));

        assertDoesNotThrow(() ->
            service.track(EventType.INSTANCE_CREATED, 1L, 2L, null, "user1", null, Map.of())
        );
    }

    @Test
    void shouldPassNullEventData() {
        service.track(EventType.STEP_COMPLETED, 1L, 2L, 3L, null, null, null);

        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getEventData());
    }

    @Test
    void shouldTrackAllEventTypes() {
        for (EventType type : EventType.values()) {
            service.track(type, 1L, 1L, 1L, "u1", "IOS", Map.of());
        }
        verify(eventLogMapper, times(EventType.values().length)).insert(any(EventLog.class));
    }
}
