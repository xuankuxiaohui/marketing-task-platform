package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.identity.query.AuditQuery;
import com.mkt.kernel.PageQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AuditQueryAppServiceTest {

    @Test
    void buildsFilterSqlAndPages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        AuditQueryAppService service = new AuditQueryAppService(jdbc);
        var page = service.page(new AuditQuery(
                1L, "auth", "login", "FAILURE", Instant.parse("2026-08-01T00:00:00Z"), null, PageQuery.of(1, 20)));
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).isEmpty();
    }
}
