package app.morphe.extension.newx.timeline;

import android.content.SharedPreferences;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.newx.mute.MuteModel;
import app.morphe.extension.newx.mute.MuteSettings;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteEngine;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteStore;
import app.morphe.extension.newx.mute.accountmute.AccountMuteStore;
import app.morphe.extension.newx.mute.accountmute.AccountTargets;
import app.morphe.extension.newx.settings.NewXLogger;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/** Collect evidence before filtering, so later replies can also suppress earlier rows. */
final class ScheduledMute {
    private static final KeywordMuteEngine KEYWORDS = new KeywordMuteEngine();
    private static final KeywordMuteEngine ACCOUNTS = new KeywordMuteEngine();
    private static final LinkedHashMap<Long, Long> PARENTS = new LinkedHashMap<Long, Long>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> entry) { return size() > 4096; }
    };
    private static String keywordData, accountData;
    private static final ThreadLocal<Set<Object>> REMOVED = new ThreadLocal<>();

    static Object withPass(Object items, TimelineModelAccess access, Supplier<Object> filter) {
        if (!MuteSettings.enabled(false) && !MuteSettings.enabled(true)) return filter.get();
        Set<Object> previous = REMOVED.get();
        try {
            try {
                REMOVED.set(prepare(items, access));
            } catch (Exception exception) {
                NewXLogger.printException(() -> "Scheduled mute evaluation failed", exception);
                REMOVED.remove();
            }
            return filter.get();
        } finally {
            if (previous == null) REMOVED.remove(); else REMOVED.set(previous);
        }
    }

    static boolean active() { return REMOVED.get() != null; }
    static boolean shouldRemove(Object item) {
        Set<Object> removed = REMOVED.get();
        return removed != null && removed.contains(item);
    }

    private static synchronized Set<Object> prepare(Object items, TimelineModelAccess access) throws Exception {
        SharedPreferences preferences = new PikoSharedPrefCategory(MuteSettings.SHARED_PREF_NAME).preferences;
        boolean keywords = MuteSettings.enabled(false), accounts = MuteSettings.enabled(true);
        String nextKeywords = preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "");
        if (keywords && !Objects.equals(keywordData, nextKeywords)) {
            KEYWORDS.setRules(KeywordMuteStore.load(preferences));
            keywordData = preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "");
            PARENTS.clear();
        }
        String nextAccounts = preferences.getString(MuteSettings.ACCOUNT_MUTE_RULES.key, "");
        if (accounts && !Objects.equals(accountData, nextAccounts)) {
            ACCOUNTS.setRules(AccountMuteStore.load(preferences));
            accountData = nextAccounts;
        }
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        KEYWORDS.refresh(now, zone);
        ACCOUNTS.refresh(now, zone);
        List<Object> posts = new ArrayList<>();
        collect(items, access, posts, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        Map<Object, Long> ids = new IdentityHashMap<>();
        Set<Object> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object post : posts) {
            Object result = MuteModel.postResult(post);
            Object canonical = MuteModel.canonical(result);
            long id = number(MuteModel.id(canonical));
            if (id > 0) {
                ids.put(post, id);
                PARENTS.put(id, number(MuteModel.parentId(canonical)));
            }
            Set<String> matches = new HashSet<>(), accountMatches = new HashSet<>();
            inspect(result, matches, accountMatches, keywords, accounts,
                    Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            if (keywords && !matches.isEmpty()) {
                KEYWORDS.rememberTweet(id, matches);
                removed.add(post);
            }
            if (accounts && !accountMatches.isEmpty()) removed.add(post);
        }
        if (keywords && MuteSettings.conversations()) {
            Set<Long> roots = new HashSet<>();
            for (Long id : new ArrayList<>(PARENTS.keySet())) {
                if (KEYWORDS.isTweetMuted(id, now)) roots.add(root(id));
            }
            for (Object post : posts) {
                Long id = ids.get(post);
                if (id != null && roots.contains(root(id))) removed.add(post);
            }
        }
        return removed;
    }

    private static long root(long id) {
        Set<Long> seen = new HashSet<>();
        while (seen.add(id)) {
            Long parent = PARENTS.get(id);
            if (parent == null || parent <= 0) return id;
            id = parent;
        }
        return id;
    }

    private static void inspect(Object result, Set<String> words, Set<String> handles,
                                boolean keywords, boolean accounts, Set<Object> seen, int depth) {
        if (result == null || depth > 16 || !seen.add(result)) return;
        Object canonical = MuteModel.canonical(result);
        if (canonical == null) return;
        if (keywords) {
            KEYWORDS.collectMatches((String) MuteModel.text(canonical), words);
            KEYWORDS.collectMatches((String) MuteModel.noteText(MuteModel.noteResult(MuteModel.note(canonical))), words);
        }
        if (accounts) {
            Object name = MuteModel.screenName(MuteModel.author(canonical));
            if (name instanceof String) {
                try { ACCOUNTS.collectExactMatches(AccountTargets.normalize((String) name), handles); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        inspect(MuteModel.quoted(result), words, handles, keywords, accounts, seen, depth + 1);
        inspect(MuteModel.reposted(result), words, handles, keywords, accounts, seen, depth + 1);
    }

    private static void collect(Object value, TimelineModelAccess access, List<Object> posts,
                                Set<Object> seen, int depth) {
        if (value == null || depth > 32 || !seen.add(value)) return;
        if (access.isPost(value)) posts.add(value);
        else if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) collect(child, access, posts, seen, depth + 1);
        } else if (access.isModuleItem(value)) collect(access.getModuleItem(value), access, posts, seen, depth + 1);
        else if (access.isModule(value)) collect(access.getModuleChildren(value), access, posts, seen, depth + 1);
    }

    private static long number(Object value) {
        try { return value == null ? 0 : Long.parseLong(value.toString()); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
