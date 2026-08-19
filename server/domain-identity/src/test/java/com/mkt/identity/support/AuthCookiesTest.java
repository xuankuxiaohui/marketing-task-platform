package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookiesTest {

    @Test
    void writeReadAndBearer() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthCookies.writeSession(response, "admin:tok");
        AuthCookies.writeCsrf(response, "csrf");
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("SameSite=Strict").contains("HttpOnly");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(AuthCookies.SESSION, "admin:tok"), new Cookie(AuthCookies.CSRF, "csrf"));
        assertThat(AuthCookies.read(req, AuthCookies.SESSION)).isEqualTo("admin:tok");
        req.addHeader("Authorization", "Bearer client:abc");
        assertThat(AuthCookies.readBearer(req)).isEqualTo("client:abc");
        AuthCookies.clear(response);
        assertThat(response.getHeaders("Set-Cookie").stream().anyMatch(h -> h.contains("Max-Age=0"))).isTrue();
    }
}
