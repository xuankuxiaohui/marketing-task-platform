package com.mkt.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Cross-domain read-only crowd membership (DEC-003 / F05 / F06 combo). Owned by domain-task. */
public interface CrowdPort {

    /** Upper bound for {@link #memberOfAll} / {@link #memberOfAny} code lists; excess → false. */
    int MAX_COMBO_CODES = 32;

    /** Whether {@code userId} is in an ENABLED crowd pack by id. Missing/disabled = false. */
    boolean memberOf(long crowdId, long userId);

    /** Whether {@code userId} is in an ENABLED crowd pack by code. Missing/disabled = false. */
    boolean memberOfCode(String crowdCode, long userId);

    /**
     * True iff {@code userId} is in every listed ENABLED crowd code. Empty / over
     * {@link #MAX_COMBO_CODES} / null → false. Blank entries ignored; no expression engine.
     */
    default boolean memberOfAll(Collection<String> crowdCodes, long userId) {
        List<String> codes = normalizeCodes(crowdCodes);
        if (codes.isEmpty() || codes.size() > MAX_COMBO_CODES) {
            return false;
        }
        for (String code : codes) {
            if (!memberOfCode(code, userId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True iff {@code userId} is in at least one listed ENABLED crowd code. Empty / over
     * {@link #MAX_COMBO_CODES} / null → false. Blank entries ignored; no expression engine.
     */
    default boolean memberOfAny(Collection<String> crowdCodes, long userId) {
        List<String> codes = normalizeCodes(crowdCodes);
        if (codes.isEmpty() || codes.size() > MAX_COMBO_CODES) {
            return false;
        }
        for (String code : codes) {
            if (memberOfCode(code, userId)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizeCodes(Collection<String> crowdCodes) {
        if (crowdCodes == null || crowdCodes.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(crowdCodes.size());
        for (String code : crowdCodes) {
            if (code != null && !code.isBlank()) {
                out.add(code.trim());
            }
        }
        return out;
    }
}
