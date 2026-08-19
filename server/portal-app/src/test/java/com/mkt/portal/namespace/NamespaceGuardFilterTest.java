package com.mkt.portal.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class NamespaceGuardFilterTest {

    private final NamespaceGuardFilter filter = new NamespaceGuardFilter();

    @Test
    void allowedPrefixContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/common/task/list");
        request.setRequestURI("/api/common/task/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void adminPrefixReturns404WithoutChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/auth/login");
        request.setRequestURI("/admin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.getStatus()).isEqualTo(404);
    }

    private static final class RecordingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            called = true;
        }
    }
}
