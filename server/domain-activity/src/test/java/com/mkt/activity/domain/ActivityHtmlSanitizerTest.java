package com.mkt.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityHtmlSanitizerTest {

    @Test
    void stripsScriptIframeOnAndJavascriptUrl() {
        String raw = """
                <p onclick="alert(1)">hello</p>
                <script>alert(1)</script>
                <iframe src="https://evil.test"></iframe>
                <a href="javascript:alert(1)">x</a>
                <img src="https://ok.test/a.png" onerror="alert(1)">
                """;
        String clean = ActivityHtmlSanitizer.sanitize(raw);
        String lower = clean.toLowerCase();
        assertThat(lower).doesNotContain("<script");
        assertThat(lower).doesNotContain("<iframe");
        assertThat(lower).doesNotContain("onclick");
        assertThat(lower).doesNotContain("onerror");
        assertThat(lower).doesNotContain("javascript:");
        assertThat(clean).contains("<p>");
        assertThat(clean).contains("hello");
        assertThat(clean).contains("src=\"https://ok.test/a.png\"");
    }

    @Test
    void keepsSafeMarkupAndEncodesText() {
        String clean = ActivityHtmlSanitizer.sanitize("<p>a < b & c</p><strong>ok</strong>");
        assertThat(clean).contains("<p>");
        assertThat(clean).contains("&lt;");
        assertThat(clean).contains("&amp;");
        assertThat(clean).contains("<strong>ok</strong>");
    }

    @Test
    void blankAndCommentOnlyBecomeEmpty() {
        assertThat(ActivityHtmlSanitizer.sanitize("   ")).isEmpty();
        assertThat(ActivityHtmlSanitizer.sanitize("<!-- xss --><script>x</script>")).isEmpty();
    }

    @Test
    void entityEncodedJavascriptHrefIsDropped() {
        String clean = ActivityHtmlSanitizer.sanitize("<a href=\"javascript&#58;alert(1)\">x</a>");
        assertThat(clean.toLowerCase()).doesNotContain("javascript");
        assertThat(clean).doesNotContain("href=");
    }
}
