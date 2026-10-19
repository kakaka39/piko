/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.accountmute;

import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteEngine;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Cache immutable post identities, so newly starting schedules apply to known posts too. */
public final class AccountPostCache {
    private final Map<Long, Set<String>> authors = new LinkedHashMap<Long, Set<String>>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Set<String>> entry) {
            return size() > 4096;
        }
    };
    public void remember(long tweetId, Set<String> handles) {
        if (tweetId <= 0) return;
        if (handles.isEmpty()) authors.remove(tweetId);
        else authors.put(tweetId, new HashSet<>(handles));
    }
    public boolean isMuted(long tweetId, KeywordMuteEngine schedules) {
        Set<String> handles = authors.get(tweetId);
        if (handles == null) return false;
        Set<String> matches = new HashSet<>();
        for (String handle : handles) {
            schedules.collectExactMatches(handle, matches);
            if (!matches.isEmpty()) return true;
        }
        return false;
    }
}
