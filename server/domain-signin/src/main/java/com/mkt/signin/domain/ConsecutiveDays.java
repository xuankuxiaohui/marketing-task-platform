package com.mkt.signin.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Live streak ending at today if signed, otherwise yesterday (R21.2).
 * A missed yesterday resets the chain to 0 until the next checkin/catchup.
 */
public final class ConsecutiveDays {

    private ConsecutiveDays() {}

    public static int compute(Collection<LocalDate> signedDates, LocalDate today) {
        if (signedDates == null || signedDates.isEmpty() || today == null) {
            return 0;
        }
        Set<LocalDate> dates = signedDates instanceof Set<LocalDate> set ? set : new HashSet<>(signedDates);
        LocalDate start = dates.contains(today) ? today : today.minusDays(1);
        if (!dates.contains(start)) {
            return 0;
        }
        int n = 0;
        LocalDate cursor = start;
        while (dates.contains(cursor)) {
            n++;
            cursor = cursor.minusDays(1);
        }
        return n;
    }
}
