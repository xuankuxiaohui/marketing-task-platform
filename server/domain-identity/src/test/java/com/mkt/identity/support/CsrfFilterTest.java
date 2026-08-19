package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfFilterTest {

    private final CsrfFilter filter = new CsrfFilter();

    @Test
    void getSkipsCsrf() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/admin/auth/profile"), response, chain);
        assertThat(chain.called).isTrue();
    }

    @Test
    void loginPostSkipsCsrf() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/auth/login");
        req.setRequestURI("/admin/auth/login");
        filter.doFilter(req, response, chain);
        assertThat(chain.called).isTrue();
    }

    @Test
    void writeWithoutTokenIs403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/admin/auth/password");
        req.setRequestURI("/admin/auth/password");
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("common.permission-denied");
    }

    @Test
    void matchingCookieAndHeaderPasses() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/admin/auth/password");
        req.setRequestURI("/admin/auth/password");
        req.setCookies(new Cookie(AuthCookies.CSRF, "abc"));
        req.addHeader(AuthCookies.CSRF_HEADER, "abc");
        filter.doFilter(req, response, chain);
        assertThat(chain.called).isTrue();
    }

    private static FilterChain unused() {
        return (req, res) -> {
            throw new IllegalStateException("should not continue");
        };
    }

    private static final class RecordingChain implements FilterChain {
        boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
