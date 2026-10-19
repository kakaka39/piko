/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Locale;

/** Immutable rule. Weekly windows use device-local wall times and [start, end). */
public final class KeywordMuteRule {
    public enum Mode { ALWAYS, UNTIL, WEEKLY }

    public final String id, keyword, normalizedKeyword, groupId;
    public final boolean enabled;
    public final Mode mode;
    public final long until;
    public final int startDay, startMinute, endDay, endMinute;

    public KeywordMuteRule(String id, String keyword, boolean enabled, Mode mode, long until,
                           int startDay, int startMinute, int endDay, int endMinute) {
        this(id, keyword, enabled, mode, until, startDay, startMinute, endDay, endMinute, id);
    }

    public KeywordMuteRule(String id, String keyword, boolean enabled, Mode mode, long until,
                           int startDay, int startMinute, int endDay, int endMinute, String groupId) {
        if (groupId == null || groupId.isEmpty()) throw new IllegalArgumentException("Empty mute group");
        this.groupId = groupId;
        if (id == null || id.isEmpty() || keyword == null || mode == null
                || keyword.contains("\n") || keyword.contains("\r")) {
            throw new IllegalArgumentException("Invalid keyword mute rule");
        }
        this.normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) throw new IllegalArgumentException("Empty keyword");
        this.schedule = new MuteSchedule(enabled, mode, until, startDay, startMinute, endDay, endMinute);
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

    private final MuteSchedule schedule;

    public long activeUntil(long now, ZoneId zone) { return schedule.activeUntil(now, zone); }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n').toLowerCase(Locale.ROOT).trim();
        StringBuilder visible = new StringBuilder(normalized.length());
        normalized.codePoints().filter(c -> Character.getType(c) != Character.FORMAT)
                .forEach(visible::appendCodePoint);
        return visible.toString().trim();
    }
}
