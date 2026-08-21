package com.mkt.ad.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** R30.5: carousel weight desc, materialId asc; single-slot take max weight (id smaller). */
public final class AdSelector {

    private AdSelector() {}

    public static List<AdCandidate> rank(List<AdCandidate> eligible) {
        if (eligible == null || eligible.isEmpty()) {
            return List.of();
        }
        List<AdCandidate> copy = new ArrayList<>(eligible);
        copy.sort(Comparator.comparingInt(AdCandidate::weight)
                .reversed()
                .thenComparingLong(AdCandidate::materialId));
        return List.copyOf(copy);
    }

    public static List<AdCandidate> take(String form, List<AdCandidate> ranked, int carouselMax) {
        List<AdCandidate> ordered = rank(ranked);
        if (ordered.isEmpty()) {
            return List.of();
        }
        if (AdForms.CAROUSEL.equals(AdForms.require(form))) {
            int max = Math.max(1, carouselMax);
            return ordered.subList(0, Math.min(max, ordered.size()));
        }
        return List.of(ordered.getFirst());
    }
}
