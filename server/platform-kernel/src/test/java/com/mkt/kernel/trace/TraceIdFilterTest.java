package com.mkt.kernel.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.seenTraceId).isNotBlank();
        assertThat(chain.seenTraceId).matches("[a-f0-9]{32}");
        assertThat(response.getHeader(TraceIds.HEADER)).isEqualTo(chain.seenTraceId);
        assertThat(TraceIds.current()).isEmpty();
        assertThat(UserContext.current()).isEmpty();
    }

    @Test
    void reusesLegalIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ping");
        request.addHeader(TraceIds.HEADER, "abc-trace_01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.seenTraceId).isEqualTo("abc-trace_01");
        assertThat(response.getHeader(TraceIds.HEADER)).isEqualTo("abc-trace_01");
    }

    @Test
    void rejectsIllegalHeaderAndClearsUserContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ping");
        request.addHeader(TraceIds.HEADER, "bad header");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserContext.set(new UserPrincipal(1L, "admin", "root"));

        filter.doFilter(request, response, (req, resp) -> {
            assertThat(TraceIds.current()).isNotEqualTo("bad header");
            assertThat(UserContext.current()).contains(new UserPrincipal(1L, "admin", "root"));
        });

        assertThat(UserContext.current()).isEmpty();
        assertThat(response.getHeader(TraceIds.HEADER)).isNotEqualTo("bad header");
    }

    private static final class CapturingChain implements FilterChain {
        private String seenTraceId;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            seenTraceId = TraceIds.current();
        }
    }
}
