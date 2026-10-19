/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.accountmute;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteRule;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteStore;
import app.morphe.extension.twitter.settings.Settings;

public final class AccountMuteStore {
    private AccountMuteStore() { }
    public static synchronized List<KeywordMuteRule> load(SharedPreferences preferences) throws JSONException {
        String key = Settings.ACCOUNT_MUTE_RULES.key;
        return preferences.contains(key) ? decode(preferences.getString(key, "")) : new ArrayList<>();
    }
    public static List<KeywordMuteRule> decode(String encoded) throws JSONException {
        JSONObject root = new JSONObject(encoded);
        int version = root.getInt("version");
        if (version != 1 && version != 2) throw new JSONException("Unsupported account rule version");
        root.put("version", 1);
        List<KeywordMuteRule> rules = KeywordMuteStore.decode(root.toString(), "account", version == 2);
        for (int i = 0; i < rules.size(); i++) {
            KeywordMuteRule rule = rules.get(i);
            try {
                if (!AccountTargets.normalize(rule.keyword).equals(rule.keyword)) throw new IllegalArgumentException();
                String groupId = root.getJSONArray("rules").getJSONObject(i).optString("groupId", rule.id);
                rules.set(i, copy(rule.id, rule.keyword, rule, groupId));
            } catch (IllegalArgumentException e) { throw new JSONException("Invalid account identifier"); }
        }
        return rules;
    }
    public static synchronized void save(SharedPreferences preferences, List<KeywordMuteRule> rules) throws JSONException {
        JSONObject root = new JSONObject(KeywordMuteStore.encode(rules, "account")).put("version", 2);
        for (int i = 0; i < rules.size(); i++) root.getJSONArray("rules").getJSONObject(i).put("groupId", rules.get(i).groupId);
        String encoded = root.toString();
        decode(encoded);
        preferences.edit().putString(Settings.ACCOUNT_MUTE_RULES.key, encoded).apply();
    }
    public static List<KeywordMuteRule> rulesForImport(JSONObject data) throws JSONException {
        return data.has(Settings.ACCOUNT_MUTE_RULES.key)
                ? decode(data.getString(Settings.ACCOUNT_MUTE_RULES.key)) : null;
    }

    public static List<KeywordMuteRule> withAccounts(List<KeywordMuteRule> current, List<String> handles,
                                                    KeywordMuteRule period, String replacedId) {
        return withSchedules(current, handles, period, replacedId == null
                ? Collections.emptySet() : Collections.singleton(replacedId));
    }

    /** Empty IDs append schedules; editing changes only the explicitly selected schedules. */
    public static List<KeywordMuteRule> withSchedules(List<KeywordMuteRule> current, List<String> handles,
                                                     KeywordMuteRule period, Set<String> editedIds) {
        if (handles.isEmpty()) throw new IllegalArgumentException("Empty account batch");
        Set<String> remaining = new HashSet<>(editedIds), covered = new HashSet<>();
        Set<String> editedGroups = new HashSet<>();
        for (KeywordMuteRule rule : current) if (editedIds.contains(rule.id)) editedGroups.add(rule.groupId);
        String newGroup = editedGroups.size() == 1 ? editedGroups.iterator().next() : UUID.randomUUID().toString();
        List<KeywordMuteRule> updated = new ArrayList<>();
        for (KeywordMuteRule rule : current) {
            if (!editedIds.contains(rule.id)) { updated.add(rule); continue; }
            remaining.remove(rule.id);
            if (handles.contains(rule.keyword)) {
                updated.add(copy(rule.id, rule.keyword, period, rule.groupId));
                covered.add(rule.keyword);
            }
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Selected schedule no longer exists");
        for (String handle : new java.util.LinkedHashSet<>(handles)) {
            if (!AccountTargets.normalize(handle).equals(handle)) throw new IllegalArgumentException("Invalid account");
            if (!covered.contains(handle)) updated.add(copy(UUID.randomUUID().toString(), handle, period, newGroup));
        }
        List<KeywordMuteRule> ordered = new ArrayList<>();
        for (List<KeywordMuteRule> group : groups(updated)) {
            if (group.get(0).groupId.equals(newGroup)) {
                group.sort(java.util.Comparator.comparingInt(rule -> handles.indexOf(rule.keyword)));
            }
            ordered.addAll(group);
        }
        return ordered;
    }

    /** Keep creation batches distinct, even when they have identical schedules. */
    public static List<List<KeywordMuteRule>> groups(List<KeywordMuteRule> rules) {
        LinkedHashMap<List<Object>, List<KeywordMuteRule>> groups = new LinkedHashMap<>();
        for (KeywordMuteRule rule : rules) {
            // A partially edited imported group must not hide differing schedule settings.
            List<Object> key = java.util.Arrays.asList(rule.groupId, rule.enabled, rule.mode, rule.until,
                    rule.startDay, rule.startMinute, rule.endDay, rule.endMinute);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule);
        }
        return new ArrayList<>(groups.values());
    }

    private static KeywordMuteRule copy(String id, String handle, KeywordMuteRule period, String groupId) {
        return new KeywordMuteRule(id, handle, period.enabled, period.mode, period.until,
                period.startDay, period.startMinute, period.endDay, period.endMinute, groupId);
    }
}
