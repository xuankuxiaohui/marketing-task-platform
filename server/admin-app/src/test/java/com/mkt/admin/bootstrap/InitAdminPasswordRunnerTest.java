package com.mkt.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

class InitAdminPasswordRunnerTest {

    @Test
    void skipsWhenHashAlreadySet() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("admin"))).thenReturn("$2a$12$already");

        new InitAdminPasswordRunner(jdbc, "secret").run(null);

        verify(jdbc, never()).update(anyString(), anyString(), anyString());
    }

    @Test
    void warnsWhenEnvMissing() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("admin"))).thenReturn("");

        new InitAdminPasswordRunner(jdbc, null).run(null);

        verify(jdbc, never()).update(anyString(), anyString(), anyString());
    }

    @Test
    void writesBcryptWhenEnvPresent() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("admin"))).thenReturn("");

        new InitAdminPasswordRunner(jdbc, "Init#Pass1").run(null);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), hash.capture(), eq("admin"));
        assertThat(hash.getValue()).startsWith("$2a$12$");
    }

    @Test
    void scannedRunnerMustNotUseOnBeanCondition() {
        assertThat(InitAdminPasswordRunner.class.getAnnotation(Component.class)).isNotNull();
        assertThat(InitAdminPasswordRunner.class.getAnnotation(ConditionalOnBean.class)).isNull();
        assertThat(ApplicationRunner.class.isAssignableFrom(InitAdminPasswordRunner.class)).isTrue();
    }

    @Test
    void hasExactlyOneConstructorSoSpringCanInstantiate() {
        assertThat(InitAdminPasswordRunner.class.getDeclaredConstructors()).hasSize(1);
    }
}
