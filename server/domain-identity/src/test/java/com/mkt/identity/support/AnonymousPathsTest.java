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
        assertThat(AnonymousPaths.portalAnonymous("POST", "/internal/task/callback")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/internal/task/progress")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/task/list")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/task/8/detail")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/activity/activities")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/activity/3")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/points/balance")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/signin/activities")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/signin/1/calendar")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/task/mine")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/task/8/start")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/activity/3/participate")).isFalse();
        assertThat(AnonymousPaths.portalAnonymous("GET", "/api/common/dict/province")).isFalse();
        assertThat(AnonymousPaths.portalOptionalAuth("POST", "/api/common/track/batch")).isTrue();
        assertThat(AnonymousPaths.portalOptionalAuth("GET", "/api/common/ad/positions/home")).isTrue();
        assertThat(AnonymousPaths.portalAnonymous("POST", "/api/common/ad/materials/9/dismiss")).isTrue();
        assertThat(AnonymousPaths.portalOptionalAuth("POST", "/api/common/ad/materials/9/dismiss")).isTrue();
        assertThat(AnonymousPaths.portalOptionalAuth("GET", "/api/common/activity/activities")).isTrue();
        assertThat(AnonymousPaths.portalOptionalAuth("GET", "/api/common/task/list")).isTrue();
        assertThat(AnonymousPaths.portalOptionalAuth("POST", "/api/common/auth/login")).isFalse();
        assertThat(AnonymousPaths.portalOptionalAuth("GET", "/api/common/captcha")).isFalse();
        assertThat(AnonymousPaths.portalOptionalAuth("GET", "/api/common/auth/username-available")).isFalse();
        assertThat(AnonymousPaths.portalOptionalAuth("POST", "/api/common/auth/register")).isFalse();
    }
}
