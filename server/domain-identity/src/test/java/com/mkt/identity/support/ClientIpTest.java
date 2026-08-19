package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void prefersForwardedThenRemote() {
        assertThat(ClientIp.of(null)).isEqualTo("0.0.0.0");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        assertThat(ClientIp.of(req)).isEqualTo("10.0.0.1");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        assertThat(ClientIp.of(req)).isEqualTo("1.1.1.1");
    }
}
