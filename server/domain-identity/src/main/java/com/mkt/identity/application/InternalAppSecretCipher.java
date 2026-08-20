package com.mkt.identity.application;

import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.entity.InternalAppEntity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM for {@code sys_internal_app} secrets. Master key = {@code MKT_INTERNAL_APP_AES_KEY}. */
@Component
public class InternalAppSecretCipher {

    public static final String ENV_NAME = "MKT_INTERNAL_APP_AES_KEY";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public InternalAppSecretCipher(@Value("${MKT_INTERNAL_APP_AES_KEY:}") String hexKey) {
        this.key = parseKey(hexKey);
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("internal app secret encrypt failed", ex);
        }
    }

    public String decrypt(String cipherText) {
        requireKey();
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText is required");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            if (raw.length <= IV_LENGTH) {
                throw new IllegalStateException("internal app secret cipher too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("internal app secret decrypt failed", ex);
        }
    }

    public List<String> decryptActive(InternalAppEntity app, Instant now) {
        List<String> secrets = new ArrayList<>(2);
        secrets.add(decrypt(app.getSecretCipher()));
        Instant expireAt = IdentityTime.toInstant(app.getPrevExpireAt());
        if (app.getPrevSecretCipher() != null && expireAt != null && now.isBefore(expireAt)) {
            secrets.add(decrypt(app.getPrevSecretCipher()));
        }
        return List.copyOf(secrets);
    }

    public SecureRandom random() {
        return random;
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(ENV_NAME + " is required");
        }
    }

    private static SecretKey parseKey(String hexKey) {
        if (hexKey == null || hexKey.isBlank()) {
            return null;
        }
        String trimmed = hexKey.trim();
        if (trimmed.length() != 64 || !trimmed.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException(ENV_NAME + " must be 64 hex characters");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(trimmed), "AES");
    }
}
