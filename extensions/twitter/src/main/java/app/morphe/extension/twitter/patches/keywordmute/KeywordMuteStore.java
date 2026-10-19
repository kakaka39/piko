/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import app.morphe.extension.twitter.settings.Settings;

public final class KeywordMuteStore {
    private KeywordMuteStore() {}

    public static synchronized List<KeywordMuteRule> load(SharedPreferences preferences) throws JSONException {
        String key = Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key;
        if (preferences.contains(key)) return decode(preferences.getString(key, ""));
        List<KeywordMuteRule> rules = migrate(preferences.getString(
                Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key, ""));
        save(preferences, rules);
        return rules;
    }

    public static List<KeywordMuteRule> migrate(String legacy) {
        List<KeywordMuteRule> rules = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : legacy.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String normalized = KeywordMuteRule.normalize(line);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                rules.add(new KeywordMuteRule(UUID.randomUUID().toString(), line, true,
                        KeywordMuteRule.Mode.ALWAYS, 0, 1, 0, 1, 1));
            }
        }
        return rules;
    }

    public static String encode(List<KeywordMuteRule> rules) throws JSONException {
        return encode(rules, "keyword");
    }

    public static String encode(List<KeywordMuteRule> rules, String targetField) throws JSONException {
        JSONArray array = new JSONArray();
        for (KeywordMuteRule rule : rules) {
            array.put(new JSONObject().put("id", rule.id).put(targetField, rule.keyword)
                    .put("enabled", rule.enabled).put("mode", rule.mode.name()).put("until", rule.until)
                    .put("startDay", rule.startDay).put("startMinute", rule.startMinute)
                    .put("endDay", rule.endDay).put("endMinute", rule.endMinute));
        }
        return new JSONObject().put("version", 1).put("rules", array).toString();
    }

    public static List<KeywordMuteRule> decode(String encoded) throws JSONException {
        return decode(encoded, "keyword");
    }

    public static List<KeywordMuteRule> decode(String encoded, String targetField) throws JSONException {
        return decode(encoded, targetField, false);
    }

    public static List<KeywordMuteRule> decode(String encoded, String targetField, boolean repeatedTargets) throws JSONException {
        JSONObject root = new JSONObject(encoded);
        if (root.getInt("version") != 1) throw new JSONException("Unsupported mute rule version");
        JSONArray array = root.getJSONArray("rules");
        List<KeywordMuteRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>(), keywords = new HashSet<>();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                KeywordMuteRule rule = new KeywordMuteRule(item.getString("id"), item.getString(targetField),
                        item.getBoolean("enabled"), KeywordMuteRule.Mode.valueOf(item.getString("mode")),
                        item.getLong("until"), item.getInt("startDay"), item.getInt("startMinute"),
                        item.getInt("endDay"), item.getInt("endMinute"));
                if (!ids.add(rule.id) || (!repeatedTargets && !keywords.add(rule.normalizedKeyword))) {
                    throw new IllegalArgumentException("Duplicate mute rule");
                }
                rules.add(rule);
            }
        } catch (IllegalArgumentException e) {
            throw new JSONException("Invalid mute rule");
        }
        return rules;
    }

    public static synchronized void save(SharedPreferences preferences, List<KeywordMuteRule> rules)
            throws JSONException {
        String encoded = encode(rules);
        decode(encoded); // Validate the entire value before replacing any existing data.
        preferences.edit().putString(Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, encoded)
                .remove(Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key).apply();
    }

    /** Apply one period to selected rules while preserving keywords, IDs and unrelated rules. */
    public static List<KeywordMuteRule> withSchedules(List<KeywordMuteRule> current,
                                                     KeywordMuteRule period, Set<String> editedIds) {
        if (editedIds.isEmpty()) throw new IllegalArgumentException("No selected keywords");
        Set<String> remaining = new HashSet<>(editedIds);
        List<KeywordMuteRule> updated = new ArrayList<>();
        for (KeywordMuteRule rule : current) {
            if (!editedIds.contains(rule.id)) { updated.add(rule); continue; }
            remaining.remove(rule.id);
            updated.add(new KeywordMuteRule(rule.id, rule.keyword, period.enabled, period.mode, period.until,
                    period.startDay, period.startMinute, period.endDay, period.endMinute));
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Selected keyword no longer exists");
        return updated;
    }

    /** Validate before the general importer writes any settings. New format takes precedence. */
    public static List<KeywordMuteRule> rulesForImport(JSONObject data) throws JSONException {
        if (data.has(Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key)) {
            return decode(data.getString(Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key));
        }
        if (data.has(Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key)) {
            return migrate(data.getString(Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key));
        }
        return null;
    }
}
