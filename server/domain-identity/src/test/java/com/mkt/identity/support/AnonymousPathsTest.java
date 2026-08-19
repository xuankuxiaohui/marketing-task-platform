package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnonymousPathsTest {

    @Test
    void adminAnonymousClosedList() {
        assertThat(AnonymousPaths.adminAnonymous("GET", "/admin/captcha")).isTrue();
        assertThat(AnonymousPaths.adminAnonymous("POST", "/admin/auth/login")).isTrue();
        assertThat(AnonymousPaths.adminAnonymous("POST", "/admin/auth/logout")).isFalse();
        assertThat(AnonymousPaths.adminAnonymous("PUT", "/admin/auth/password")).isFalse();
    }

    @Test
    void portalAnonymousClosedList() {
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/captcha")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/auth/username-available")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/auth/register")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/auth/login")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/track/batch")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/ad/positions/home")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/task/list")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/dict/province")).isFalse();
    }
}
