package com.mkt.portal.internal;

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
import com.mkt.identity.support.InternalAuthFilter;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.json.JsonUtil;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * §7.7 A-01~A-06. Expanding samples = add JSON under {@code internal-attack/}.
 * Rejected samples must not invoke the downstream chain (业务表零变化).
 */
class ReplayAttackIT {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final String AES_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String APP_ID = "appidabcdefghij";
    private static final String DISABLED_APP = "disabledappid01";
    private static final String SECRET = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcde";
    private static final String PATH = "/internal/task/callback";
    private static final String BODY = "{\"instanceId\":1,\"stepCode\":\"cb\",\"bizNo\":\"biz-1\"}";
    private static final String TAMPERED = "{\"instanceId\":1,\"stepCode\":\"cb\",\"bizNo\":\"hack\"}";

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void attackSampleMatchesExpectation(Sample sample) throws Exception {
        Fixture fixture = Fixture.create();
        AtomicInteger downstream = new AtomicInteger();
        FilterChain chain = (req, res) -> downstream.incrementAndGet();
        if ("ok".equals(sample.expect)) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            fixture.filter.doFilter(fixture.request(sample), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(downstream.get()).isEqualTo(1);
            return;
        }
        if ("REPLAY".equals(sample.mutation)) {
            fixture.filter.doFilter(
                    fixture.signed(NOW.toEpochMilli(), "n-replay", BODY, APP_ID, SECRET, BODY),
                    new MockHttpServletResponse(),
                    chain);
            assertThat(downstream.get()).isEqualTo(1);
        }
        int before = downstream.get();
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(fixture.request(sample), response, chain);
        assertThat(response.getStatus()).as(sample.id).isEqualTo(sample.http);
        assertThat(response.getContentAsString()).as(sample.id).contains(sample.expect);
        assertThat(downstream.get()).as(sample.id + " 业务表零变化").isEqualTo(before);
    }

    static Stream<Sample> samples() throws IOException {
        URL root = ReplayAttackIT.class.getResource("/internal-attack");
        assertThat(root).isNotNull();
        Path dir = Path.of(URI.create(root.toString()));
        List<Sample> loaded = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "A-*.json")) {
            for (Path file : stream) {
                try (InputStream in = Files.newInputStream(file)) {
                    loaded.add(JsonUtil.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Sample.class));
                }
            }
        }
        loaded.sort(Comparator.comparing(Sample::id));
        assertThat(loaded).hasSizeGreaterThanOrEqualTo(8);
        return loaded.stream();
    }

    public record Sample(String id, String mutation, String expect, int http) {
        @Override
        public String toString() {
            return id + " " + mutation;
        }
    }

    private static final class Fixture {
        final InternalAuthFilter filter;
        final Clock clock;

        private Fixture(InternalAuthFilter filter, Clock clock) {
            this.filter = filter;
            this.clock = clock;
        }

        static Fixture create() {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            InternalAppMapper mapper = mock(InternalAppMapper.class);
            InternalAppSecretCipher cipher = new InternalAppSecretCipher(AES_KEY);
            when(mapper.getByAppId(APP_ID)).thenReturn(app(cipher, APP_ID, "ENABLED", SECRET));
            when(mapper.getByAppId(DISABLED_APP)).thenReturn(app(cipher, DISABLED_APP, "DISABLED", SECRET));
            MemoryKeyValueStore kv = new MemoryKeyValueStore();
            InternalHmacVerifier verifier = new InternalHmacVerifier(
                    mapper, cipher, new InternalAppSecretCache(), new NonceStore(kv), (key, def) -> def, clock);
            SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
            when(limiter.tryAcquire(eq(RateLimitDim.APP_ID), anyString(), eq(1), anyInt())).thenReturn(true);
            InternalAuthFilter filter = new InternalAuthFilter(verifier, limiter, (key, def) -> def);
            return new Fixture(filter, clock);
        }

        MockHttpServletRequest request(Sample sample) {
            long now = clock.millis();
            return switch (sample.mutation) {
                case "REPLAY" -> signed(now, "n-replay", BODY, APP_ID, SECRET, BODY);
                case "TAMPER_BODY" -> signed(now, "n-tamper", TAMPERED, APP_ID, SECRET, BODY);
                case "SKEW_PLUS" -> signed(now + 301_000L, "n-plus", BODY, APP_ID, SECRET, BODY);
                case "SKEW_MINUS" -> signed(now - 301_000L, "n-minus", BODY, APP_ID, SECRET, BODY);
                case "FUTURE_WITHIN" -> signed(now + 299_000L, "n-future", BODY, APP_ID, SECRET, BODY);
                case "UNKNOWN_APP" -> signed(now, "n-unknown", BODY, "unknown-app-id", SECRET, BODY);
                case "DISABLED_APP" -> signed(now, "n-disabled", BODY, DISABLED_APP, SECRET, BODY);
                case "EMPTY_SIGN" -> unsigned("n-empty", "");
                case "MALFORMED_SIGN" -> unsigned("n-malformed", "not-a-hex-signature");
                default -> throw new IllegalArgumentException(sample.mutation);
            };
        }

        MockHttpServletRequest signed(
                long timestamp, String nonce, String body, String appId, String secret, String signedBody) {
            String sign = InternalHmacs.signHex(
                    secret,
                    InternalHmacs.stringToSign(
                            "POST", PATH, String.valueOf(timestamp), nonce, signedBody.getBytes(StandardCharsets.UTF_8)));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
            request.setRequestURI(PATH);
            request.setContentType("application/json");
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            request.addHeader("X-App-Id", appId);
            request.addHeader("X-Timestamp", String.valueOf(timestamp));
            request.addHeader("X-Nonce", nonce);
            request.addHeader("X-Sign", sign);
            request.addHeader("X-Trace-Id", "must-not-enter-string-to-sign");
            return request;
        }

        MockHttpServletRequest unsigned(String nonce, String sign) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
            request.setRequestURI(PATH);
            request.setContentType("application/json");
            request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
            request.addHeader("X-App-Id", APP_ID);
            request.addHeader("X-Timestamp", String.valueOf(clock.millis()));
            request.addHeader("X-Nonce", nonce);
            request.addHeader("X-Sign", sign);
            return request;
        }

        private static InternalAppEntity app(InternalAppSecretCipher cipher, String appId, String status, String secret) {
            InternalAppEntity entity = new InternalAppEntity();
            entity.setAppId(appId);
            entity.setStatus(status);
            entity.setSecretCipher(cipher.encrypt(secret));
            return entity;
        }
    }
}
