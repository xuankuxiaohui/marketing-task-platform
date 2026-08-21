package com.mkt.task.support;

import com.mkt.task.domain.Platforms;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code X-Client-Platform} (R16.4). Missing or illegal values become WEB and increment
 * the unknown-platform counter.
 */
@Component
public class ClientPlatformResolver {

    private final AtomicLong unknownPlatform = new AtomicLong();

    public String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            unknownPlatform.incrementAndGet();
            return Platforms.WEB;
        }
        String value = raw.trim().toUpperCase();
        if (Platforms.valid(value)) {
            return value;
        }
        unknownPlatform.incrementAndGet();
        return Platforms.WEB;
    }

    public long unknownPlatformCount() {
        return unknownPlatform.get();
    }
}
