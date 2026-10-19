import android.content.SharedPreferences;
import app.morphe.extension.twitter.patches.keywordmute.*;
import app.morphe.extension.twitter.settings.Settings;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

public final class KeywordMuteStoreTest {
    private static int checks;
    private static final String RULES = Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key;
    private static final String LEGACY = Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key;
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private interface CheckedAction { void run() throws Exception; }
    private static void invalid(CheckedAction action, String name) throws Exception {
        try { action.run(); } catch (org.json.JSONException expected) { checks++; return; }
        throw new AssertionError(name);
    }
    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String, String> data = new HashMap<>();
        int writes;
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public String getString(String key, String fallback) { return data.getOrDefault(key, fallback); }
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, String> pending = new HashMap<>();
                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { pending.put(key, null); return this; }
                @Override public void apply() {
                    for (Map.Entry<String, String> entry : pending.entrySet()) {
                        if (entry.getValue() == null) data.remove(entry.getKey());
                        else data.put(entry.getKey(), entry.getValue());
                    }
                    writes++;
                }
            };
        }
    }
    public static void main(String[] args) throws Exception {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.data.put(LEGACY, " ＳＰＯＩＬＥＲ\r\nspoiler\rOther\n\n\u200b");
        List<KeywordMuteRule> migrated = KeywordMuteStore.load(prefs);
        check(migrated.size() == 2, "deduplicate normalized legacy entries and ignore empty entries");
        check(migrated.stream().allMatch(r -> r.enabled && r.mode == KeywordMuteRule.Mode.ALWAYS), "legacy rules remain permanent");
        check(prefs.contains(RULES) && !prefs.contains(LEGACY) && prefs.writes == 1, "atomic migration");
        List<KeywordMuteRule> second = KeywordMuteStore.load(prefs);
        check(second.get(0).id.equals(migrated.get(0).id) && prefs.writes == 1, "migration is idempotent");
        long expiry = Instant.parse("2026-10-20T14:45:00Z").toEpochMilli();
        List<KeywordMuteRule> all = Arrays.asList(migrated.get(0),
                new KeywordMuteRule("until", "deadline", false, KeywordMuteRule.Mode.UNTIL, expiry, 1, 0, 1, 1),
                new KeywordMuteRule("weekly", "weekly", true, KeywordMuteRule.Mode.WEEKLY, 0, 7, 1380, 1, 30));
        KeywordMuteStore.save(prefs, all);
        List<KeywordMuteRule> restored = KeywordMuteStore.decode(KeywordMuteStore.encode(all));
        check(restored.size() == 3 && restored.get(1).until == expiry && !restored.get(1).enabled, "deadline and enable state roundtrip");
        check(restored.get(2).startDay == 7 && restored.get(2).startMinute == 1380
                && restored.get(2).endDay == 1 && restored.get(2).endMinute == 30, "weekly rule roundtrip");
        JSONObject backup = new JSONObject().put(RULES, KeywordMuteStore.encode(all)).put(LEGACY, "stale legacy keyword");
        check(KeywordMuteStore.rulesForImport(backup).size() == 3, "new format takes precedence over stale legacy data");
        KeywordMuteStore.save(prefs, KeywordMuteStore.rulesForImport(new JSONObject().put(LEGACY, "replacement")));
        check(KeywordMuteStore.load(prefs).size() == 1
                && KeywordMuteStore.load(prefs).get(0).keyword.equals("replacement"), "old backup replaces current mute rules");
        check(KeywordMuteStore.rulesForImport(new JSONObject()) == null, "unrelated imports leave mute rules alone");
        KeywordMuteStore.save(prefs, Collections.emptyList());
        prefs.data.put(LEGACY, "must not return");
        check(KeywordMuteStore.load(prefs).isEmpty(), "saved empty list never resurrects legacy keywords");
        prefs.data.put(RULES, "{broken");
        String broken = prefs.data.get(RULES);
        invalid(() -> KeywordMuteStore.load(prefs), "invalid settings rejected");
        check(prefs.data.get(RULES).equals(broken), "invalid settings preserved");
        invalid(() -> KeywordMuteStore.decode("{\"version\":2,\"rules\":[]}"), "future schema rejected");
        invalid(() -> KeywordMuteStore.decode(KeywordMuteStore.encode(Arrays.asList(all.get(0), all.get(0)))), "duplicate rules rejected");
        JSONObject invalidMode = new JSONObject(KeywordMuteStore.encode(all));
        invalidMode.getJSONArray("rules").getJSONObject(0).put("mode", "UNKNOWN");
        invalid(() -> KeywordMuteStore.rulesForImport(new JSONObject().put(RULES, invalidMode.toString())), "unknown modes never become permanent");
        Set<String> selectedIds = new HashSet<>(Arrays.asList(all.get(0).id, all.get(1).id));
        List<KeywordMuteRule> bulk = KeywordMuteStore.withSchedules(all, all.get(1), selectedIds);
        check(bulk.get(0).id.equals(all.get(0).id) && bulk.get(0).keyword.equals(all.get(0).keyword)
                && bulk.get(1).id.equals(all.get(1).id), "bulk period update preserves exact keywords and IDs");
        check(bulk.get(0).until == expiry && bulk.get(0).mode == KeywordMuteRule.Mode.UNTIL
                && !bulk.get(0).enabled && !bulk.get(1).enabled, "bulk period and enabled state applied to every selection");
        check(bulk.get(2) == all.get(2) && all.get(0).mode == KeywordMuteRule.Mode.ALWAYS,
                "unselected rules and source list remain untouched");
        MemoryPreferences bulkPrefs = new MemoryPreferences();
        KeywordMuteStore.save(bulkPrefs, bulk);
        check(KeywordMuteStore.load(bulkPrefs).get(0).until == expiry, "bulk update roundtrip");
        int writes = bulkPrefs.writes;
        try {
            KeywordMuteStore.save(bulkPrefs, KeywordMuteStore.withSchedules(all, all.get(1),
                    new HashSet<>(Arrays.asList(all.get(0).id, "missing"))));
            throw new AssertionError("stale selection must fail");
        } catch (IllegalArgumentException expected) {
            check(bulkPrefs.writes == writes, "stale bulk selection never partially saves");
        }
        try {
            KeywordMuteStore.withSchedules(all, all.get(1), Collections.emptySet());
            throw new AssertionError("empty selection must fail");
        } catch (IllegalArgumentException expected) { check(true, "empty bulk selection rejected"); }
        System.out.println("PASS: " + checks + " migration, storage and backup checks");
    }
}
