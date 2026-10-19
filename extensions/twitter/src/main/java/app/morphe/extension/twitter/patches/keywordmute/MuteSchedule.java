/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Shared minute-precision period used by keyword and account rules. */
public final class MuteSchedule {
    public final KeywordMuteRule.Mode mode;
    public final boolean enabled;
    public final long until;
    public final int startDay, startMinute, endDay, endMinute;
    public MuteSchedule(boolean enabled, KeywordMuteRule.Mode mode, long until,
                        int startDay, int startMinute, int endDay, int endMinute) {
        if (mode == null) throw new IllegalArgumentException("Missing period");
        if (mode == KeywordMuteRule.Mode.UNTIL && (until <= 0 || until % 60000 != 0)) {
            throw new IllegalArgumentException("Expiry must be a positive whole minute");
        }
        if (startDay < 1 || startDay > 7 || endDay < 1 || endDay > 7
                || startMinute < 0 || startMinute >= 1440 || endMinute < 0 || endMinute >= 1440
                || (mode == KeywordMuteRule.Mode.WEEKLY && startDay == endDay && startMinute == endMinute)) {
            throw new IllegalArgumentException("Invalid weekly interval");
        }
        this.enabled = enabled; this.mode = mode; this.until = until;
        this.startDay = startDay; this.startMinute = startMinute;
        this.endDay = endDay; this.endMinute = endMinute;
    }

    /** The end of the current active occurrence, or MIN_VALUE if inactive. */
    public long activeUntil(long now, ZoneId zone) {
        if (!enabled) return Long.MIN_VALUE;
        if (mode == KeywordMuteRule.Mode.ALWAYS) return Long.MAX_VALUE;
        if (mode == KeywordMuteRule.Mode.UNTIL) return now < until ? until : Long.MIN_VALUE;
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

}
