package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileTagsTest {

    @Test
    void normalizeDropsBlankAndEnforcesLimit() {
        assertThat(ProfileTags.normalize(List.of(" a ", "", "b"))).containsExactly("a", "b");
        assertThat(ProfileTags.normalize(null)).isEmpty();
        List<String> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            twenty.add("t" + i);
        }
        assertThat(ProfileTags.withinLimit(twenty)).isTrue();
        twenty.add("extra");
        assertThat(ProfileTags.withinLimit(twenty)).isFalse();
    }
}
