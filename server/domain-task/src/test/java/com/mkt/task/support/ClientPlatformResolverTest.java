package com.mkt.task.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.domain.Platforms;
import org.junit.jupiter.api.Test;

/** R16.4: whitelist parse; missing/illegal → WEB and unknown-platform counter. */
class ClientPlatformResolverTest {

    @Test
    void fivePlatformsPassThrough() {
        ClientPlatformResolver resolver = new ClientPlatformResolver();
        assertThat(resolver.resolve("WEB")).isEqualTo(Platforms.WEB);
        assertThat(resolver.resolve("ANDROID")).isEqualTo(Platforms.ANDROID);
        assertThat(resolver.resolve("IOS")).isEqualTo(Platforms.IOS);
        assertThat(resolver.resolve("MINIAPP")).isEqualTo(Platforms.MINIAPP);
        assertThat(resolver.resolve("SIMULATOR")).isEqualTo(Platforms.SIMULATOR);
        assertThat(resolver.unknownPlatformCount()).isZero();
    }

    @Test
    void missingAndIllegalFallBackToWebAndCount() {
        ClientPlatformResolver resolver = new ClientPlatformResolver();
        assertThat(resolver.resolve(null)).isEqualTo(Platforms.WEB);
        assertThat(resolver.resolve("")).isEqualTo(Platforms.WEB);
        assertThat(resolver.resolve("pc")).isEqualTo(Platforms.WEB);
        assertThat(resolver.resolve(" android ")).isEqualTo(Platforms.ANDROID);
        assertThat(resolver.unknownPlatformCount()).isEqualTo(3);
    }
}
