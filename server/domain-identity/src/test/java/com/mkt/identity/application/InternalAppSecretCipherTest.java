package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.identity.entity.InternalAppEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InternalAppSecretCipherTest {

    private final InternalAppSecretCipher cipher = new InternalAppSecretCipher(InternalAppAppServiceTest.TEST_KEY);

    @Test
    void encryptDecryptRoundTripAndDualLiveWindow() {
        String secret = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcde";
        String cipherText = cipher.encrypt(secret);
        assertThat(cipherText).isNotEqualTo(secret);
        assertThat(cipher.decrypt(cipherText)).isEqualTo(secret);

        InternalAppEntity app = new InternalAppEntity();
        app.setSecretCipher(cipher.encrypt("newSecretnewSecretnewSecretnewSecretnewSe"));
        app.setPrevSecretCipher(cipher.encrypt("oldSecretoldSecretoldSecretoldSecretoldSe"));
        app.setPrevExpireAt(LocalDateTime.ofInstant(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));
        assertThat(cipher.decryptActive(app, Instant.parse("2026-08-20T11:00:00Z")))
                .containsExactly("newSecretnewSecretnewSecretnewSecretnewSe", "oldSecretoldSecretoldSecretoldSecretoldSe");
        assertThat(cipher.decryptActive(app, Instant.parse("2026-08-20T12:00:00Z")))
                .containsExactly("newSecretnewSecretnewSecretnewSecretnewSe");
    }

    @Test
    void missingKeyRejected() {
        InternalAppSecretCipher missing = new InternalAppSecretCipher("");
        assertThatThrownBy(() -> missing.encrypt("x")).isInstanceOf(IllegalStateException.class);
    }
}
