package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrgIdsTest {

    @Test
    void acceptsCharsetAndRejectsBlankOrIllegal() {
        assertThat(OrgIds.normalizeOrNull("org-north_01")).isEqualTo("org-north_01");
        assertThat(OrgIds.normalizeOrNull(" 42 ")).isEqualTo("42");
        assertThat(OrgIds.normalizeOrNull("")).isNull();
        assertThat(OrgIds.normalizeOrNull("bad org")).isNull();
        assertThat(OrgIds.normalizeOrNull("x".repeat(65))).isNull();
    }
}
