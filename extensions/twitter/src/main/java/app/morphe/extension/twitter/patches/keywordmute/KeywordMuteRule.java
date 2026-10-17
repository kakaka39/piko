/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Immutable rule. Weekly windows use device-local wall times and [start, end). */
public final class KeywordMuteRule {
    public enum Mode { ALWAYS, UNTIL, WEEKLY }

    public final String id, keyword, normalizedKeyword;
    public final boolean enabled;
    public final Mode mode;
    public final long until;
    public final int startDay, startMinute, endDay, endMinute;

    public KeywordMuteRule(String id, String keyword, boolean enabled, Mode mode, long until,
                           int startDay, int startMinute, int endDay, int endMinute) {
        if (id == null || id.isEmpty() || keyword == null || mode == null
                || keyword.contains("\n") || keyword.contains("\r")) {
            throw new IllegalArgumentException("Invalid keyword mute rule");
        }
        this.normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) throw new IllegalArgumentException("Empty keyword");
        if (mode == Mode.UNTIL && (until <= 0 || until % 60000 != 0)) {
            throw new IllegalArgumentException("Expiry must be a positive whole minute");
        }
        if (startDay < 1 || startDay > 7 || endDay < 1 || endDay > 7
                || startMinute < 0 || startMinute >= 1440 || endMinute < 0 || endMinute >= 1440
                || (mode == Mode.WEEKLY && startDay == endDay && startMinute == endMinute)) {
            throw new IllegalArgumentException("Invalid weekly interval");
        }
        this.id = id;
        this.keyword = keyword.trim();
        this.enabled = enabled;
        this.mode = mode;
        this.until = until;
        this.startDay = startDay;
        this.startMinute = startMinute;
        this.endDay = endDay;
        this.endMinute = endMinute;
    }

    /** The end of the current active occurrence, or MIN_VALUE if inactive. */
    public long activeUntil(long now, ZoneId zone) {
        if (!enabled) return Long.MIN_VALUE;
        if (mode == Mode.ALWAYS) return Long.MAX_VALUE;
        if (mode == Mode.UNTIL) return now < until ? until : Long.MIN_VALUE;
        LocalDate monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int week = -1; week <= 0; week++) {
            LocalDate base = monday.plusWeeks(week);
            long start = base.plusDays(startDay - 1).atStartOfDay().plusMinutes(startMinute)
                    .atZone(zone).toInstant().toEpochMilli();
            int endOffset = (endDay - 1) * 1440 + endMinute;
            int startOffset = (startDay - 1) * 1440 + startMinute;
            // atZone shifts nonexistent times forward; overlaps use the earlier offset.
            long end = base.plusDays(endDay - 1).plusWeeks(endOffset <= startOffset ? 1 : 0)
                    .atStartOfDay().plusMinutes(endMinute).atZone(zone).toInstant().toEpochMilli();
            if (now >= start && now < end) return end;
        }
        return Long.MIN_VALUE;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n').toLowerCase(Locale.ROOT).trim();
        StringBuilder visible = new StringBuilder(normalized.length());
        normalized.codePoints().filter(c -> Character.getType(c) != Character.FORMAT)
                .forEach(visible::appendCodePoint);
        return visible.toString();
    }
}
