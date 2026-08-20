package com.mkt.task.domain;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 5-field cron, minute granularity, min interval ≥ 1 hour (R11.7). */
public final class CronExprs {

    private CronExprs() {}

    public static boolean valid(String expr) {
        if (expr == null || expr.isBlank() || expr.length() > 32) {
            return false;
        }
        String[] fields = expr.trim().split("\\s+");
        if (fields.length != 5) {
            return false;
        }
        int[] minutes = expand(fields[0], 0, 59);
        int[] hours = expand(fields[1], 0, 23);
        int[] days = expand(fields[2], 1, 31);
        int[] months = expand(fields[3], 1, 12);
        int[] weeks = expandWeek(fields[4]);
        if (minutes == null || hours == null || days == null || months == null || weeks == null) {
            return false;
        }
        return minIntervalSeconds(minutes, hours) >= 3600;
    }

    public static ZonedDateTime lastAtOrBefore(String expr, ZonedDateTime now) {
        ParsedCron parsed = parse(expr);
        if (parsed == null || now == null) {
            return null;
        }
        ZonedDateTime cursor = now.truncatedTo(ChronoUnit.MINUTES);
        int limit = 366 * 24 * 60;
        for (int i = 0; i < limit; i++) {
            if (!cursor.isAfter(now) && matches(parsed, cursor)) {
                return cursor;
            }
            cursor = cursor.minusMinutes(1);
        }
        return null;
    }

    public static ZonedDateTime nextAfter(String expr, ZonedDateTime now) {
        ParsedCron parsed = parse(expr);
        if (parsed == null || now == null) {
            return null;
        }
        ZonedDateTime cursor = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        int limit = 366 * 24 * 60;
        for (int i = 0; i < limit; i++) {
            if (matches(parsed, cursor)) {
                return cursor;
            }
            cursor = cursor.plusMinutes(1);
        }
        return null;
    }

    private static ParsedCron parse(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        String[] fields = expr.trim().split("\\s+");
        if (fields.length != 5) {
            return null;
        }
        int[] minutes = expand(fields[0], 0, 59);
        int[] hours = expand(fields[1], 0, 23);
        int[] days = expand(fields[2], 1, 31);
        int[] months = expand(fields[3], 1, 12);
        int[] weeks = expandWeek(fields[4]);
        if (minutes == null || hours == null || days == null || months == null || weeks == null) {
            return null;
        }
        return new ParsedCron(minutes, hours, days, months, weeks);
    }

    private static boolean matches(ParsedCron parsed, ZonedDateTime time) {
        if (!contains(parsed.minutes, time.getMinute()) || !contains(parsed.hours, time.getHour())) {
            return false;
        }
        if (!contains(parsed.months, time.getMonthValue())) {
            return false;
        }
        boolean dayStar = parsed.days.length == 31;
        boolean weekStar = parsed.weeks.length >= 7;
        boolean dayHit = contains(parsed.days, time.getDayOfMonth());
        int dow = time.getDayOfWeek().getValue() % 7;
        boolean weekHit = contains(parsed.weeks, dow) || (dow == 0 && contains(parsed.weeks, 7));
        if (dayStar && weekStar) {
            return true;
        }
        if (dayStar) {
            return weekHit;
        }
        if (weekStar) {
            return dayHit;
        }
        return dayHit || weekHit;
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private record ParsedCron(int[] minutes, int[] hours, int[] days, int[] months, int[] weeks) {}

    private static int minIntervalSeconds(int[] minutes, int[] hours) {
        List<Integer> times = new ArrayList<>();
        for (int hour : hours) {
            for (int minute : minutes) {
                times.add(hour * 60 + minute);
            }
        }
        if (times.size() <= 1) {
            return 24 * 3600;
        }
        times.sort(Integer::compareTo);
        int minGap = Integer.MAX_VALUE;
        for (int i = 1; i < times.size(); i++) {
            minGap = Math.min(minGap, times.get(i) - times.get(i - 1));
        }
        int wrap = times.get(0) + 24 * 60 - times.get(times.size() - 1);
        minGap = Math.min(minGap, wrap);
        return minGap * 60;
    }

    private static int[] expand(String field, int min, int max) {
        if ("*".equals(field)) {
            return range(min, max, 1);
        }
        if (field.startsWith("*/")) {
            int step = parseInt(field.substring(2));
            if (step <= 0) {
                return null;
            }
            return range(min, max, step);
        }
        String[] parts = field.split(",");
        List<Integer> values = new ArrayList<>();
        for (String part : parts) {
            int dash = part.indexOf('-');
            int slash = part.indexOf('/');
            if (slash >= 0 && dash >= 0) {
                int from = parseInt(part.substring(0, dash));
                int to = parseInt(part.substring(dash + 1, slash));
                int step = parseInt(part.substring(slash + 1));
                if (invalid(from, min, max) || invalid(to, min, max) || step <= 0) {
                    return null;
                }
                for (int v = from; v <= to; v += step) {
                    values.add(v);
                }
            } else if (dash >= 0) {
                int from = parseInt(part.substring(0, dash));
                int to = parseInt(part.substring(dash + 1));
                if (invalid(from, min, max) || invalid(to, min, max) || from > to) {
                    return null;
                }
                for (int v = from; v <= to; v++) {
                    values.add(v);
                }
            } else {
                int v = parseInt(part);
                if (invalid(v, min, max)) {
                    return null;
                }
                values.add(v);
            }
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] expandWeek(String field) {
        String normalized = field.toUpperCase(Locale.ROOT)
                .replace("SUN", "0")
                .replace("MON", "1")
                .replace("TUE", "2")
                .replace("WED", "3")
                .replace("THU", "4")
                .replace("FRI", "5")
                .replace("SAT", "6");
        return expand(normalized, 0, 7);
    }

    private static int[] range(int min, int max, int step) {
        List<Integer> values = new ArrayList<>();
        for (int v = min; v <= max; v += step) {
            values.add(v);
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean invalid(int value, int min, int max) {
        return value < min || value > max;
    }
}
