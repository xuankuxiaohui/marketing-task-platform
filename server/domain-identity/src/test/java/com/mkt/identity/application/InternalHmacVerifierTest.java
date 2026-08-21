package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.InternalHmacs;
import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.support.InternalAuthErrorCodes;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.time.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalHmacVerifierTest {

    static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    static final String APP_ID = "appidabcdefghij";
    static final String SECRET = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcde";
    static final String PATH = "/internal/task/callback";
    static final byte[] BODY = InternalHmacs.utf8("{\"instanceId\":1,\"stepCode\":\"cb\"}");

    private InternalAppMapper mapper;
    private InternalAppSecretCipher cipher;
    private InternalAppSecretCache cache;
    private MemoryKeyValueStore kv;
    private NonceStore nonces;
    private MutableClock clock;
    private InternalHmacVerifier verifier;
    private InternalAppEntity enabled;

    @BeforeEach
    void setUp() {
        mapper = mock(InternalAppMapper.class);
        cipher = new InternalAppSecretCipher(InternalAppAppServiceTest.TEST_KEY);
        cache = new InternalAppSecretCache();
        kv = new MemoryKeyValueStore();
        nonces = new NonceStore(kv);
        clock = new MutableClock(NOW);
        verifier = new InternalHmacVerifier(mapper, cipher, cache, nonces, (key, def) -> def, clock);
        enabled = app("ENABLED", SECRET, null, null);
        when(mapper.getByAppId(APP_ID)).thenReturn(enabled);
    }

    @Test
    void validRequestThenReplayIsRejected() {
        String nonce = "n-replay";
        String sign = sign(SECRET, NOW.toEpochMilli(), nonce, BODY);
        assertThat(verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, BODY)).isEqualTo(APP_ID);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.NONCE_REPLAYED);
    }

    @Test
    void tamperedBodyKeepsOriginalSignatureInvalid() {
        String nonce = "n-tamper";
        String sign = sign(SECRET, NOW.toEpochMilli(), nonce, BODY);
        byte[] tampered = InternalHmacs.utf8("{\"instanceId\":1,\"stepCode\":\"other\"}");
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.INVALID_SIGNATURE);
        assertThat(kv.get(NonceStore.key(APP_ID, nonce))).isNull();
    }

    @Test
    void timestampSkewPlusAndMinus301Rejected() {
        Instant plus = NOW.plusSeconds(301);
        Instant minus = NOW.minusSeconds(301);
        assertSkew(plus);
        assertSkew(minus);
    }

    @Test
    void futureTimestampWithinToleranceAndNewNonceAccepted() {
        Instant future = NOW.plusSeconds(299);
        String nonce = "n-future";
        String sign = sign(SECRET, future.toEpochMilli(), nonce, BODY);
        assertThat(verifier.authenticate("POST", PATH, APP_ID, ts(future), nonce, sign, BODY)).isEqualTo(APP_ID);
    }

    @Test
    void unknownAndDisabledAppRejected() {
        when(mapper.getByAppId("unknown-app")).thenReturn(null);
        String nonce = "n-unknown";
        String sign = sign(SECRET, NOW.toEpochMilli(), nonce, BODY);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, "unknown-app", ts(NOW), nonce, sign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.APP_NOT_FOUND);

        InternalAppEntity disabled = app("DISABLED", SECRET, null, null);
        when(mapper.getByAppId("disabled-app")).thenReturn(disabled);
        String disabledSign = sign(SECRET, NOW.toEpochMilli(), "n-disabled", BODY);
        assertThatThrownBy(
                        () -> verifier.authenticate(
                                "POST", PATH, "disabled-app", ts(NOW), "n-disabled", disabledSign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.APP_DISABLED);
    }

    @Test
    void emptyAndMalformedSignatureRejected() {
        assertInvalidSign(null);
        assertInvalidSign("");
        assertInvalidSign("not-hex");
        assertInvalidSign("zz" + "0".repeat(62));
        assertInvalidSign("0".repeat(63));
    }

    @Test
    void dualKeyWindowAcceptsPrevSecretUntilExpiry() {
        String prev = "oldSecretoldSecretoldSecretoldSecretoldSe";
        String current = "newSecretnewSecretnewSecretnewSecretnewSe";
        InternalAppEntity rotated = app("ENABLED", current, prev, NOW.plus(Duration.ofHours(24)));
        when(mapper.getByAppId(APP_ID)).thenReturn(rotated);
        cache.evict(APP_ID);
        String nonce = "n-prev";
        String sign = sign(prev, NOW.toEpochMilli(), nonce, BODY);
        assertThat(verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, BODY)).isEqualTo(APP_ID);

        clock.setInstant(NOW.plus(Duration.ofHours(24)));
        String laterNonce = "n-prev-expired";
        String laterSign = sign(prev, clock.instant().toEpochMilli(), laterNonce, BODY);
        assertThatThrownBy(
                        () -> verifier.authenticate("POST", PATH, APP_ID, ts(clock.instant()), laterNonce, laterSign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.INVALID_SIGNATURE);
        String currentSign = sign(current, clock.instant().toEpochMilli(), "n-current", BODY);
        assertThat(verifier.authenticate("POST", PATH, APP_ID, ts(clock.instant()), "n-current", currentSign, BODY))
                .isEqualTo(APP_ID);
    }

    @Test
    void redisDownRejectsNonceWithoutFailOpen() {
        kv.setAvailable(false);
        String nonce = "n-down";
        String sign = sign(SECRET, NOW.toEpochMilli(), nonce, BODY);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.NONCE_REPLAYED);
    }

    @Test
    void missingHeadersAndOverlongNonceAreParamInvalid() {
        String sign = sign(SECRET, NOW.toEpochMilli(), "n", BODY);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, null, ts(NOW), "n", sign, BODY))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(NOW), "n".repeat(65), sign, BODY))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void traceHeaderDoesNotEnterStringToSign() {
        String nonce = "n-trace";
        String sign = sign(SECRET, NOW.toEpochMilli(), nonce, BODY);
        assertThat(verifier.authenticate("POST", PATH, APP_ID, ts(NOW), nonce, sign, BODY)).isEqualTo(APP_ID);
    }

    private void assertSkew(Instant at) {
        String nonce = "n-skew-" + at.toEpochMilli();
        String sign = sign(SECRET, at.toEpochMilli(), nonce, BODY);
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(at), nonce, sign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.TIMESTAMP_SKEW);
        assertThat(kv.get(NonceStore.key(APP_ID, nonce))).isNull();
    }

    private void assertInvalidSign(String sign) {
        assertThatThrownBy(() -> verifier.authenticate("POST", PATH, APP_ID, ts(NOW), "n-bad-sign", sign, BODY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(InternalAuthErrorCodes.INVALID_SIGNATURE);
        assertThat(kv.get(NonceStore.key(APP_ID, "n-bad-sign"))).isNull();
    }

    private InternalAppEntity app(String status, String secret, String prev, Instant prevExpire) {
        InternalAppEntity entity = new InternalAppEntity();
        entity.setAppId(APP_ID);
        entity.setStatus(status);
        entity.setSecretCipher(cipher.encrypt(secret));
        if (prev != null) {
            entity.setPrevSecretCipher(cipher.encrypt(prev));
            entity.setPrevExpireAt(IdentityTime.toUtc(prevExpire));
        }
        return entity;
    }

    private String sign(String secret, long timestamp, String nonce, byte[] body) {
        return InternalHmacs.signHex(secret, InternalHmacs.stringToSign("POST", PATH, String.valueOf(timestamp), nonce, body));
    }

    private static String ts(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }
}
