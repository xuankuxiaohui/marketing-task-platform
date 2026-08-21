package com.mkt.ad.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdSelectorTest {

    @Test
    void carouselSortsWeightDescThenMaterialIdAscAndCaps() {
        List<AdCandidate> ranked = AdSelector.take(
                AdForms.CAROUSEL,
                List.of(candidate(3, 10), candidate(1, 10), candidate(2, 50), candidate(4, 1)),
                3);
        assertThat(ranked).extracting(AdCandidate::materialId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void singleSlotTakesHighestWeightThenSmallerId() {
        List<AdCandidate> splash = AdSelector.take(
                AdForms.SPLASH, List.of(candidate(9, 20), candidate(4, 20), candidate(1, 5)), 5);
        assertThat(splash).extracting(AdCandidate::materialId).containsExactly(4L);
        List<AdCandidate> image =
                AdSelector.take(AdForms.IMAGE, List.of(candidate(8, 1), candidate(2, 99)), 5);
        assertThat(image).extracting(AdCandidate::materialId).containsExactly(2L);
    }

    private static AdCandidate candidate(long id, int weight) {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-31T00:00:00Z");
        return new AdCandidate(
                id, weight, "t", null, "https://cdn.example/" + id + ".png", "NONE", Map.of(), start, end, start, end,
                List.of("WEB"), "NONE", null, null);
    }
}
