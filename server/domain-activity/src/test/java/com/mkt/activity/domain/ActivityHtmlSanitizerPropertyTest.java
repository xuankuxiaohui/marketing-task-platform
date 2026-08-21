package com.mkt.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

class ActivityHtmlSanitizerPropertyTest {

    @Property(tries = 80)
    void sanitizedNeverContainsForbiddenConstructs(@ForAll @StringLength(max = 400) String raw) {
        String clean = ActivityHtmlSanitizer.sanitize(raw).toLowerCase();
        assertThat(clean).doesNotContain("<script");
        assertThat(clean).doesNotContain("<iframe");
        assertThat(clean).doesNotContain("href=\"javascript:");
        assertThat(clean).doesNotContain("src=\"javascript:");
        assertThat(clean).doesNotContain("<img onerror");
        assertThat(clean).doesNotContain("<p onclick");
        assertThat(clean).doesNotContain("<div onload");
    }
}
