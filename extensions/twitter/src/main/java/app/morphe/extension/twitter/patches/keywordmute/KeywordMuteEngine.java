/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Caller serializes access. Evidence expires at the end of its original occurrence. */
public final class KeywordMuteEngine {
    private List<KeywordMuteRule> rules = new ArrayList<>();
    private final Map<String, KeywordMuteRule> active = new HashMap<>();
    private final Map<String, Long> ends = new HashMap<>();
    private final Map<Long, Map<String, Long>> tweets = boundedCache();
    private final Map<Long, Map<String, Long>> conversations = boundedCache();
    private long minute = Long.MIN_VALUE;
    private ZoneId zone;

    private static Map<Long, Map<String, Long>> boundedCache() {
        return new LinkedHashMap<Long, Map<String, Long>>(64, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Map<String, Long>> entry) {
                return size() > 4096;
            }
        };
    }

    public void setRules(List<KeywordMuteRule> value) {
        rules = new ArrayList<>(value);
        clearEvidence();
        minute = Long.MIN_VALUE;
    }

    public void clearEvidence() {
        tweets.clear();
        conversations.clear();
    }

    public void refresh(long now, ZoneId currentZone) {
        long currentMinute = Math.floorDiv(now, 60000);
        if (currentMinute == minute && currentZone.equals(zone)) return;
        // Wall-clock/timezone changes must not extend previously observed evidence.
        if (currentMinute < minute || (zone != null && !zone.equals(currentZone))) clearEvidence();
        minute = currentMinute;
        zone = currentZone;
        active.clear();
        ends.clear();
        for (KeywordMuteRule rule : rules) {
            long end = rule.activeUntil(now, currentZone);
            if (end > now) {
                active.put(rule.id, rule);
                ends.put(rule.id, end);
            }
        }
    }

    public boolean hasActiveRules() { return !active.isEmpty(); }

    public void collectMatches(String text, Set<String> matches) {
        String normalized = KeywordMuteRule.normalize(text);
        if (normalized.isEmpty()) return;
        for (KeywordMuteRule rule : active.values()) {
            if (normalized.contains(rule.normalizedKeyword)) matches.add(rule.id);
        }
    }

    private Set<String> evidence(Map<Long, Map<String, Long>> cache, long id, long now) {
        Map<String, Long> reasons = cache.get(id);
        if (reasons == null) return new HashSet<>();
        Iterator<Map.Entry<String, Long>> iterator = reasons.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> reason = iterator.next();
            if (!active.containsKey(reason.getKey()) || now >= reason.getValue()) iterator.remove();
        }
        if (reasons.isEmpty()) cache.remove(id);
        return new HashSet<>(reasons.keySet());
    }

    private void remember(Map<Long, Map<String, Long>> cache, long id, Set<String> matches) {
        if (id <= 0 || matches.isEmpty()) return;
        Map<String, Long> reasons = cache.computeIfAbsent(id, ignored -> new HashMap<>());
        for (String match : matches) {
            Long end = ends.get(match);
            if (end != null) reasons.put(match, end);
        }
    }

    public Set<String> conversationMatches(long id, long now) { return evidence(conversations, id, now); }
    public boolean isTweetMuted(long id, long now) { return !evidence(tweets, id, now).isEmpty(); }
    public void rememberTweet(long id, Set<String> matches) { remember(tweets, id, matches); }
    public void rememberConversation(long id, Set<String> matches) { remember(conversations, id, matches); }
}
