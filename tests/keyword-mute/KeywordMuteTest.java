import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteEngine;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteRule;
import java.time.*;
import java.util.*;

/** Standalone JVM regression tests; throws on failure, independently of Android UI. */
public final class KeywordMuteTest {
    private static int checks;
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static long at(String value) { return ZonedDateTime.parse(value).toInstant().toEpochMilli(); }
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static KeywordMuteRule weekly(String id, int sd, int sm, int ed, int em) {
        return new KeywordMuteRule(id, "Spoiler", true, KeywordMuteRule.Mode.WEEKLY, 0, sd, sm, ed, em);
    }
    private static void invalid(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(name);
    }
    private static Set<String> matches(KeywordMuteEngine engine, String text) {
        Set<String> result = new HashSet<>(); engine.collectMatches(text, result); return result;
    }

    public static void main(String[] args) {
        long end = at("2026-10-20T23:45:00+09:00");
        KeywordMuteRule until = new KeywordMuteRule("until", "spoiler", true, KeywordMuteRule.Mode.UNTIL,
                end, 1, 0, 1, 1);
        check(until.activeUntil(end - 1, TOKYO) == end, "active immediately before expiry");
        check(until.activeUntil(end, TOKYO) == Long.MIN_VALUE, "inactive exactly at expiry");
        check(until.activeUntil(end + 1, TOKYO) == Long.MIN_VALUE, "inactive after expiry");
        check(until.activeUntil(end - 1, ZoneId.of("America/New_York")) == end, "absolute deadline survives zone change");
        KeywordMuteRule always = new KeywordMuteRule("always", "other", true, KeywordMuteRule.Mode.ALWAYS,
                0, 1, 0, 1, 1);
        KeywordMuteRule disabled = new KeywordMuteRule("disabled", "other", false, KeywordMuteRule.Mode.ALWAYS,
                0, 1, 0, 1, 1);
        check(always.activeUntil(end, TOKYO) == Long.MAX_VALUE, "permanent rule");
        check(disabled.activeUntil(end, TOKYO) == Long.MIN_VALUE, "disabled rule");
        invalid(() -> weekly("bad", 1, 20, 1, 20), "identical start/end rejected");
        invalid(() -> weekly("bad", 0, 20, 1, 30), "invalid day rejected");
        invalid(() -> weekly("bad", 1, 1440, 1, 30), "invalid minute rejected");
        invalid(() -> new KeywordMuteRule("bad", "\u200b", true, KeywordMuteRule.Mode.ALWAYS, 0, 1, 0, 1, 1), "invisible keyword rejected");
        check(KeywordMuteRule.normalize(" ＳＰＯＩＬＥＲ\u200b ").equals("spoiler"), "existing normalization");

        KeywordMuteRule weekend = weekly("week", 6, 22 * 60, 7, 90);
        long start = at("2026-10-17T22:00:00+09:00");
        long finish = at("2026-10-18T01:30:00+09:00");
        check(weekend.activeUntil(start - 1, TOKYO) == Long.MIN_VALUE, "before weekly start");
        check(weekend.activeUntil(start, TOKYO) == finish, "inclusive weekly start");
        check(weekend.activeUntil(finish - 1, TOKYO) == finish, "overnight end before boundary");
        check(weekend.activeUntil(finish, TOKYO) == Long.MIN_VALUE, "exclusive weekly end");
        check(weekend.activeUntil(start + 7 * 86400000L, TOKYO) == finish + 7 * 86400000L, "weekly recurrence");
        KeywordMuteRule wrap = weekly("wrap", 7, 23 * 60, 1, 30);
        check(wrap.activeUntil(at("2026-10-19T00:10:00+09:00"), TOKYO)
                == at("2026-10-19T00:30:00+09:00"), "Sunday to Monday");
        KeywordMuteRule sameDay = weekly("same", 1, 60, 1, 120);
        check(sameDay.activeUntil(at("2026-10-19T01:30:00+09:00"), TOKYO)
                == at("2026-10-19T02:00:00+09:00"), "same day");
        KeywordMuteRule longWeek = weekly("long", 1, 120, 1, 60);
        check(longWeek.activeUntil(at("2026-10-25T12:00:00+09:00"), TOKYO)
                == at("2026-10-26T01:00:00+09:00"), "end earlier in week means following week");

        // Exhaust all minute boundaries in a week using independent integer interval arithmetic.
        long monday = at("2026-10-12T00:00:00+09:00");
        for (int minute = 0; minute < 10080; minute++) {
            boolean expected = minute >= 5 * 1440 + 1320 && minute < 6 * 1440 + 90;
            check((weekend.activeUntil(monday + minute * 60000L, TOKYO) > monday + minute * 60000L)
                    == expected, "weekly minute " + minute);
        }
        ZoneId ny = ZoneId.of("America/New_York");
        KeywordMuteRule dst = weekly("dst", 7, 90, 7, 210);
        check(dst.activeUntil(at("2026-03-08T03:00:00-04:00"), ny)
                == at("2026-03-08T03:30:00-04:00"), "spring DST interval");
        check(dst.activeUntil(at("2026-11-01T01:45:00-05:00"), ny)
                == at("2026-11-01T03:30:00-05:00"), "autumn DST interval");

        KeywordMuteEngine engine = new KeywordMuteEngine();
        engine.setRules(Arrays.asList(until, always, disabled));
        engine.refresh(end - 1000, TOKYO);
        Set<String> both = matches(engine, "ＳＰＯＩＬＥＲ and other");
        check(both.equals(new HashSet<>(Arrays.asList("until", "always"))), "collect all reasons, excluding disabled");
        engine.rememberTweet(1, both); engine.rememberConversation(2, both);
        engine.rememberTweet(3, Collections.singleton("until"));
        engine.refresh(end, TOKYO);
        check(engine.isTweetMuted(1, end), "other active rule keeps tweet muted");
        check(engine.conversationMatches(2, end).equals(Collections.singleton("always")), "only expired conversation reason removed");
        check(!engine.isTweetMuted(3, end), "expired tweet unmuted without parsing a new post");
        engine.setRules(Collections.singletonList(weekend));
        engine.refresh(start, TOKYO);
        engine.rememberTweet(4, matches(engine, "spoiler"));
        engine.rememberConversation(5, matches(engine, "spoiler"));
        engine.refresh(start + 7 * 86400000L, TOKYO); // No intermediate calls while inactive.
        check(!engine.isTweetMuted(4, start + 7 * 86400000L), "no stale tweet evidence next week");
        check(engine.conversationMatches(5, start + 7 * 86400000L).isEmpty(), "no stale conversation next week after sleep");
        engine.refresh(start, TOKYO); // backwards clock
        check(!engine.isTweetMuted(4, start), "backwards clock clears evidence");
        engine.rememberTweet(6, matches(engine, "spoiler"));
        engine.refresh(start, ZoneId.of("UTC"));
        check(!engine.isTweetMuted(6, start), "timezone change clears evidence");
        engine.setRules(Collections.singletonList(always)); engine.refresh(end, TOKYO);
        for (int i = 1; i <= 4097; i++) engine.rememberTweet(i, Collections.singleton("always"));
        check(!engine.isTweetMuted(1, end) && engine.isTweetMuted(4097, end), "bounded tweet cache");
        engine.setRules(Collections.emptyList()); engine.refresh(end, TOKYO);
        check(!engine.isTweetMuted(4097, end) && !engine.hasActiveRules(), "setting changes clear cached evidence");
        System.out.println("PASS: " + checks + " schedule and cache checks");
    }
}
