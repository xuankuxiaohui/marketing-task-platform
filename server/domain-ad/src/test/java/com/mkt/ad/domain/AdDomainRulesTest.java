package com.mkt.ad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.support.AdDuplicateKeys;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** Pure rules behind admin validation and portal serve. */
class AdDomainRulesTest {

    @Test
    void codesFormsStatusesAndGrayTypes() {
        assertThat(AdCodes.valid("home_banner")).isTrue();
        assertThat(AdCodes.valid("a_b-1")).isTrue();
        assertThat(AdCodes.valid(null)).isFalse();
        assertThat(AdCodes.valid("ab")).isFalse();
        assertThat(AdCodes.valid("Home")).isFalse();
        assertThat(AdCodes.valid("a".repeat(65))).isFalse();

        assertThat(AdForms.require(null)).isNull();
        assertThat(AdForms.require(" splash ")).isEqualTo(AdForms.SPLASH);
        assertThat(AdForms.valid(AdForms.FLOAT)).isTrue();
        assertThat(AdForms.valid(null)).isFalse();
        assertThat(AdForms.valid("BANNER")).isFalse();
        assertThat(AdForms.trackForm(null)).isEqualTo("image");
        assertThat(AdForms.trackForm(" carousel ")).isEqualTo("carousel");
        assertThat(AdForms.trackForm("splash")).isEqualTo("splash");
        assertThat(AdForms.trackForm("POPUP")).isEqualTo("popup");
        assertThat(AdForms.trackForm("float")).isEqualTo("float");
        assertThat(AdForms.trackForm("banner")).isEqualTo("image");
        assertThat(AdForms.singleSlot(AdForms.CAROUSEL)).isFalse();
        assertThat(AdForms.singleSlot(" carousel ")).isFalse();
        assertThat(AdForms.singleSlot(null)).isTrue();
        assertThat(AdForms.singleSlot(AdForms.IMAGE)).isTrue();

        assertThat(AdStatuses.normalize(null)).isEqualTo(AdStatuses.ENABLED);
        assertThat(AdStatuses.normalize(" disabled ")).isEqualTo(AdStatuses.DISABLED);
        assertThat(AdStatuses.valid(AdStatuses.ENABLED)).isTrue();
        assertThat(AdStatuses.valid(null)).isFalse();
        assertThat(AdStatuses.valid("ARCHIVED")).isFalse();

        assertThat(AdGrayTypes.normalize(null)).isEqualTo(AdGrayTypes.NONE);
        assertThat(AdGrayTypes.normalize(" ratio ")).isEqualTo(AdGrayTypes.RATIO);
        assertThat(AdGrayTypes.valid(" none ")).isTrue();
        assertThat(AdGrayTypes.valid("COHORT")).isFalse();
    }

    @Test
    void datesUseUtc8DayAndHalfOpenWindows() {
        Clock afternoon = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        assertThat(AdDates.dayKey(afternoon)).isEqualTo("20260820");
        Clock justBeforeMidnight = Clock.fixed(Instant.parse("2026-08-20T15:59:59Z"), ZoneOffset.UTC);
        assertThat(AdDates.dayKey(justBeforeMidnight)).isEqualTo("20260820");
        assertThat(AdDates.ttlToEndOfDay(justBeforeMidnight)).isEqualTo(Duration.ofSeconds(1));
        Clock midnight = Clock.fixed(Instant.parse("2026-08-20T16:00:00Z"), ZoneOffset.UTC);
        assertThat(AdDates.dayKey(midnight)).isEqualTo("20260821");
        assertThat(AdDates.ttlToEndOfDay(midnight)).isEqualTo(Duration.ofHours(24));

        assertThat(AdDates.toUtc(null)).isNull();
        assertThat(AdDates.toInstant(null)).isNull();
        LocalDateTime utc = AdDates.toUtc(afternoon.instant());
        assertThat(AdDates.toInstant(utc)).isEqualTo(afternoon.instant());

        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-31T00:00:00Z");
        assertThat(AdDates.inWindow(start, end, start)).isTrue();
        assertThat(AdDates.inWindow(start, end, end)).isFalse();
        assertThat(AdDates.inWindow(start, end, null)).isFalse();
        assertThat(AdDates.inWindow(null, end, start)).isFalse();
        assertThat(AdDates.overlap(start, end, end, end.plusSeconds(1))).isFalse();
        assertThat(AdDates.overlap(start, end, end.minusSeconds(1), end.plusSeconds(1))).isTrue();
        assertThat(AdDates.overlap(null, end, start, end)).isFalse();
    }

    @Test
    void grayBucketIsStableAndRatioIsExclusiveUpperBound() {
        int bucket = AdGrayBuckets.of(9L, 3L);
        assertThat(bucket).isBetween(0, 99);
        assertThat(AdGrayBuckets.of(9L, 3L)).isEqualTo(bucket);
        assertThat(AdGrayBuckets.hit(null, null, 9L, 3L)).isTrue();
        assertThat(AdGrayBuckets.hit(AdGrayTypes.NONE, 0, 9L, 3L)).isTrue();
        assertThat(AdGrayBuckets.hit(AdGrayTypes.RATIO, null, 9L, 3L)).isFalse();
        assertThat(AdGrayBuckets.hit("COHORT", 100, 9L, 3L)).isFalse();
        assertThat(AdGrayBuckets.hit(AdGrayTypes.RATIO, bucket, 9L, 3L)).isFalse();
        assertThat(AdGrayBuckets.hit(AdGrayTypes.RATIO, bucket + 1, 9L, 3L)).isTrue();
    }

    @Test
    void jumpSchemasAndPlatforms() {
        assertThat(AdJumpSchemas.normalize(null)).isEqualTo("NONE");
        assertThat(AdJumpSchemas.normalize(" link ")).isEqualTo("LINK");
        assertThat(AdJumpSchemas.validType("scheme")).isTrue();
        assertThat(AdJumpSchemas.validType("nope")).isFalse();
        assertThat(AdJumpSchemas.validParams("NONE", null)).isTrue();
        assertThat(AdJumpSchemas.validParams("ROUTE", null)).isFalse();
        assertThat(AdJumpSchemas.validParams("ROUTE", Map.of("route", " "))).isFalse();
        assertThat(AdJumpSchemas.validParams("ROUTE", Map.of("route", "a".repeat(65)))).isFalse();
        assertThat(AdJumpSchemas.validParams("ROUTE", Map.of("route", 1))).isFalse();
        assertThat(AdJumpSchemas.validParams("ROUTE", Map.of("route", "/home"))).isTrue();
        assertThat(AdJumpSchemas.validParams("LINK", Map.of("url", "http://example.com"))).isFalse();
        assertThat(AdJumpSchemas.validParams("LINK", Map.of("url", "HTTPS://example.com/a"))).isTrue();
        assertThat(AdJumpSchemas.validParams("LINK", Map.of("url", "https://" + "a".repeat(505)))).isFalse();
        assertThat(AdJumpSchemas.validParams("SCHEME", Map.of("scheme", "app://open"))).isTrue();
        assertThat(AdJumpSchemas.validParams("SCHEME", Map.of("scheme", "a".repeat(129)))).isFalse();
        assertThat(AdJumpSchemas.validParams("NOPE", Map.of())).isFalse();

        assertThat(AdPlatforms.valid(null)).isFalse();
        assertThat(AdPlatforms.valid("web")).isFalse();
        assertThat(AdPlatforms.valid(AdPlatforms.IOS)).isTrue();
        assertThat(AdPlatforms.resolve(null)).isEqualTo(AdPlatforms.WEB);
        assertThat(AdPlatforms.resolve(" ios ")).isEqualTo(AdPlatforms.IOS);
        assertThat(AdPlatforms.resolve("desktop")).isEqualTo(AdPlatforms.WEB);
        assertThat(AdPlatforms.normalize(null)).containsExactly(AdPlatforms.WEB);
        assertThat(AdPlatforms.normalize(List.of(" ", "nope"))).containsExactly(AdPlatforms.WEB);
        assertThat(AdPlatforms.normalize(List.of("android", "ANDROID", "ios"))).containsExactly("ANDROID", "IOS");
        assertThat(AdPlatforms.allows(null, AdPlatforms.WEB)).isTrue();
        assertThat(AdPlatforms.allows(List.of(), AdPlatforms.WEB)).isTrue();
        assertThat(AdPlatforms.allows(List.of(AdPlatforms.IOS), AdPlatforms.WEB)).isFalse();
    }

    @Test
    void freqKeysPreferUserThenDevice() {
        assertThat(AdFreqKeys.daily(9L, "dev", 3L, "20260820")).isEqualTo("ad:freq:9:3:20260820");
        assertThat(AdFreqKeys.daily(null, " dev ", 3L, "20260820")).isEqualTo("ad:freq:dev:dev:3:20260820");
        assertThat(AdFreqKeys.daily(null, " ", 3L, "20260820")).isEqualTo("ad:freq:dev:missing:3:20260820");
        assertThat(AdFreqKeys.popup(9L, "dev")).isEqualTo("ad:popup:cd:9");
        assertThat(AdFreqKeys.popup(null, " ")).isEqualTo("ad:popup:cd:missing");
        assertThat(AdFreqKeys.hasSubject(null, null)).isFalse();
        assertThat(AdFreqKeys.hasSubject(null, " ")).isFalse();
        assertThat(AdFreqKeys.hasSubject(null, "dev")).isTrue();
        assertThat(AdFreqKeys.hasSubject(1L, null)).isTrue();
    }

    @Test
    void fieldCodecDropsEmptyJsonAndDuplicateKeyWalksCauses() {
        assertThat(AdFieldCodec.json(null)).isNull();
        assertThat(AdFieldCodec.json(List.of())).isNull();
        assertThat(AdFieldCodec.json(Map.of())).isNull();
        assertThat(AdFieldCodec.strings(null)).isEmpty();
        assertThat(AdFieldCodec.strings(" ")).isEmpty();
        assertThat(AdFieldCodec.strings("null")).isEmpty();
        assertThat(AdFieldCodec.strings("[]")).isEmpty();
        assertThat(AdFieldCodec.strings(AdFieldCodec.json(List.of("WEB", "IOS")))).containsExactly("WEB", "IOS");
        assertThat(AdFieldCodec.map(null)).isEmpty();
        assertThat(AdFieldCodec.map(" ")).isEmpty();
        Map<String, Object> params = AdFieldCodec.map(AdFieldCodec.json(Map.of("url", "https://example.com")));
        assertThat(params).containsEntry("url", "https://example.com");
        assertThatThrownBy(() -> params.put("x", 1)).isInstanceOf(UnsupportedOperationException.class);

        assertThat(AdDuplicateKeys.duplicate(null)).isFalse();
        assertThat(AdDuplicateKeys.duplicate(new IllegalStateException("deadlock"))).isFalse();
        DuplicateKeyException dup = new DuplicateKeyException("uk");
        assertThat(AdDuplicateKeys.duplicate(dup)).isTrue();
        RuntimeException wrapped =
                new RuntimeException("wrapper", new SQLIntegrityConstraintViolationException("1062"));
        assertThat(AdDuplicateKeys.duplicate(wrapped)).isTrue();
        RuntimeException message = new RuntimeException("Duplicate entry 'home_banner'");
        assertThat(AdDuplicateKeys.duplicate(message)).isTrue();
        assertThat(AdDuplicateKeys.wrap("uk_code", message)).hasMessage("uk_code").hasCause(message);
    }
}
