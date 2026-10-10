package com.mkt.tracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.MetadataStatuses;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.mapper.EvtEventMetadataMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MybatisEventMetadataStoreTest {

    @Test
    void statusesOfEmptyOrNullSkipsMapper() {
        EvtEventMetadataMapper mapper = mock(EvtEventMetadataMapper.class);
        MybatisEventMetadataStore store = new MybatisEventMetadataStore(mapper);

        assertThat(store.statusesOf(null)).isEmpty();
        assertThat(store.statusesOf(List.of())).isEmpty();
        verify(mapper, never()).selectByEventCodes(any());
    }

    @Test
    void statusesOfQueriesOnceAndFillsMissing() {
        EvtEventMetadataMapper mapper = mock(EvtEventMetadataMapper.class);
        EvtEventMetadataEntity enabled = new EvtEventMetadataEntity();
        enabled.setEventCode("page.view");
        enabled.setStatus(MetadataStatuses.ENABLED);
        when(mapper.selectByEventCodes(any())).thenReturn(List.of(enabled));

        MybatisEventMetadataStore store = new MybatisEventMetadataStore(mapper);
        Map<String, MetadataStatus> statuses =
                store.statusesOf(List.of("page.view", "unknown.event"));

        assertThat(statuses)
                .containsEntry("page.view", MetadataStatus.ENABLED)
                .containsEntry("unknown.event", MetadataStatus.MISSING);
        verify(mapper).selectByEventCodes(List.of("page.view", "unknown.event"));
    }

    @Test
    void statusesOfAllNullCodesSkipsMapper() {
        EvtEventMetadataMapper mapper = mock(EvtEventMetadataMapper.class);
        MybatisEventMetadataStore store = new MybatisEventMetadataStore(mapper);

        assertThat(store.statusesOf(Arrays.asList(null, null))).isEmpty();
        verify(mapper, never()).selectByEventCodes(any());
    }
}
