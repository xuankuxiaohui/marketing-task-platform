package com.mkt.task.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * §7.7 M-01~M-15. Expanding samples = add files under {@code expression/malicious-*.txt}.
 */
class ExpressionSandboxMaliciousTest {

    private static final Logger log = LoggerFactory.getLogger(ExpressionSandboxMaliciousTest.class);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
    private static final CrowdResolver MISSING_CROWDS = code -> {
        log.warn("inCrowd pack missing or disabled: {}", code);
        return false;
    };

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void maliciousSampleMatchesExpectation(Sample sample) {
        String expr = sample.expression();
        if (sample.kind() == Kind.REJECT) {
            assertThatThrownBy(() -> ExpressionEngine.compile(expr))
                    .isInstanceOf(ExpressionCompileException.class);
            return;
        }
        CompiledExpression compiled = ExpressionEngine.compile(expr);
        boolean value = ExpressionEngine.evaluate(
                compiled, EvalContext.missingAttributes(CLOCK.instant(), MISSING_CROWDS));
        if (sample.kind() == Kind.EVAL_FALSE) {
            assertThat(value).isFalse();
            return;
        }
        assertThat(compiled.nodeCount()).isPositive();
    }

    static Stream<Sample> samples() throws IOException {
        URL root = ExpressionSandboxMaliciousTest.class.getResource("/expression");
        assertThat(root).isNotNull();
        Path dir = Path.of(URI.create(root.toString()));
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "malicious-*.txt")) {
            stream.forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        assertThat(files).isNotEmpty();
        List<Sample> samples = new ArrayList<>();
        for (Path file : files) {
            samples.add(read(file));
        }
        return samples.stream();
    }

    private static Sample read(Path file) throws IOException {
        String name = file.getFileName().toString();
        Kind kind;
        if (name.contains("reject")) {
            kind = Kind.REJECT;
        } else if (name.contains("eval-false")) {
            kind = Kind.EVAL_FALSE;
        } else if (name.contains("accept")) {
            kind = Kind.ACCEPT;
        } else {
            throw new IllegalArgumentException("unknown sample file " + name);
        }
        String body;
        try (InputStream in = Files.newInputStream(file)) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new Sample(name, kind, expand(body));
    }

    private static String expand(String body) {
        int nested = directive(body, "nested-parens=");
        if (nested > 0) {
            return "(".repeat(nested) + "1 = 1" + ")".repeat(nested);
        }
        int length = directive(body, "length=");
        if (length > 0) {
            return "a".repeat(length);
        }
        int nots = directive(body, "not-repeat=");
        if (nots > 0) {
            return "NOT ".repeat(nots) + "province() = 'x'";
        }
        StringBuilder expr = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!expr.isEmpty()) {
                expr.append('\n');
            }
            expr.append(line);
        }
        return expr.toString();
    }

    private static int directive(String body, String prefix) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") && trimmed.contains(prefix)) {
                int idx = trimmed.indexOf(prefix);
                return Integer.parseInt(trimmed.substring(idx + prefix.length()).trim());
            }
        }
        return 0;
    }

    enum Kind {
        REJECT,
        EVAL_FALSE,
        ACCEPT
    }

    record Sample(String file, Kind kind, String expression) {
        @Override
        public String toString() {
            return file;
        }
    }
}
