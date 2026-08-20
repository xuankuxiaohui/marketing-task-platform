package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.identity.application.InternalAppSecretCache;
import com.mkt.identity.application.InternalAppSecretCipher;
import com.mkt.identity.application.InternalHmacVerifier;
import com.mkt.identity.domain.InternalHmacs;
import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAuthFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final String AES_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String APP_ID = "appidabcdefghij";
    private static final String SECRET = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcde";
    private static final String BODY = "{\"instanceId\":1,\"stepCode\":\"cb\"}";

    private InternalAuthFilter filter;
    private MemoryKeyValueStore kv;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        InternalAppMapper mapper = mock(InternalAppMapper.class);
        InternalAppSecretCipher cipher = new InternalAppSecretCipher(AES_KEY);
        InternalAppEntity app = new InternalAppEntity();
        app.setAppId(APP_ID);
        app.setStatus("ENABLED");
        app.setSecretCipher(cipher.encrypt(SECRET));
        when(mapper.getByAppId(APP_ID)).thenReturn(app);
        kv = new MemoryKeyValueStore();
        clock = new MutableClock(NOW);
        InternalHmacVerifier verifier = new InternalHmacVerifier(
                mapper, cipher, new InternalAppSecretCache(), new NonceStore(kv), (key, def) -> def, clock);
        SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq(RateLimitDim.APP_ID), anyString(), eq(1), anyInt())).thenReturn(true);
        filter = new InternalAuthFilter(verifier, limiter, (key, def) -> def);
    }

    @Test
    void nonInternalPathPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/common/task/list");
        request.setRequestURI("/api/common/task/list");
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.called).isTrue();
    }

    @Test
    void validSignedBodyIsReplayedToDownstream() throws Exception {
        MockHttpServletRequest request = signed("n-ok", BODY);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seen.set(new String(((HttpServletRequest) req).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        });
        assertThat(seen.get()).isEqualTo(BODY);
    }

    @Test
    void replayReturnsNonceReplayedAndDoesNotCallDownstream() throws Exception {
        MockHttpServletRequest first = signed("n-same", BODY);
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse replay = new MockHttpServletResponse();
        AtomicInteger downstream = new AtomicInteger();
        filter.doFilter(signed("n-same", BODY), replay, counting(downstream));
        assertThat(replay.getStatus()).isEqualTo(400);
        assertThat(replay.getContentAsString()).contains("internal.nonce.replayed");
        assertThat(downstream.get()).isZero();
    }

    @Test
    void rateLimitIs429WithRetryAfter() throws Exception {
        SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq(RateLimitDim.APP_ID), eq(APP_ID), eq(1), anyInt())).thenReturn(false);
        InternalAuthFilter tight = filterWith(limiter);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        AtomicInteger downstream = new AtomicInteger();
        tight.doFilter(signed("n-rl-2", BODY), limited, counting(downstream));
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("1");
        assertThat(limited.getContentAsString()).contains("common.rate-limited");
        assertThat(downstream.get()).isZero();
    }

    private InternalAuthFilter filterWith(SlidingWindowRateLimiter limiter) {
        InternalAppMapper mapper = mock(InternalAppMapper.class);
        InternalAppSecretCipher cipher = new InternalAppSecretCipher(AES_KEY);
        InternalAppEntity app = new InternalAppEntity();
        app.setAppId(APP_ID);
        app.setStatus("ENABLED");
        app.setSecretCipher(cipher.encrypt(SECRET));
        when(mapper.getByAppId(APP_ID)).thenReturn(app);
        InternalHmacVerifier verifier = new InternalHmacVerifier(
                mapper, cipher, new InternalAppSecretCache(), new NonceStore(kv), (key, def) -> def, clock);
        return new InternalAuthFilter(verifier, limiter, (key, def) -> def);
    }

    private MockHttpServletRequest signed(String nonce, String body) {
        long timestamp = clock.millis();
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String sign = InternalHmacs.signHex(
                SECRET, InternalHmacs.stringToSign("POST", "/internal/task/callback", String.valueOf(timestamp), nonce, raw));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/task/callback");
        request.setRequestURI("/internal/task/callback");
        request.setContentType("application/json");
        request.setContent(raw);
        request.addHeader("X-App-Id", APP_ID);
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Sign", sign);
        request.addHeader("X-Trace-Id", "trace-not-signed");
        return request;
    }

    private static FilterChain counting(AtomicInteger counter) {
        return (req, res) -> counter.incrementAndGet();
    }

    private static final class RecordingChain implements FilterChain {
        boolean called;

        @Override
        public void doFilter(ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
