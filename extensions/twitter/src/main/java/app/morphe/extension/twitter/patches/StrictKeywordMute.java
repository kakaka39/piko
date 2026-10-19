/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.twitter.api.model.json.core.JsonApiTweet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteEngine;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteStore;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.SettingsStatus;

/** Strict substring matching for public posts parsed by X's JsonApiTweet pipeline. */
public final class StrictKeywordMute {
    private static final int MAX_TRAVERSAL_DEPTH = 4;
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final KeywordMuteEngine ENGINE = new KeywordMuteEngine();
    private static boolean enabled, conversationWideEnabled, preferencesInitialized;
    private static PikoSharedPrefCategory preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private StrictKeywordMute() {}

    private static void initializePreferences() {
        if (preferencesInitialized) return;
        try {
            preferences = new PikoSharedPrefCategory(Settings.SHARED_PREF_NAME);
            // Complete migration before observing its own preference notifications.
            KeywordMuteStore.load(preferences.preferences);
            preferenceListener = (shared, key) -> {
                if (key == null || Settings.TIMELINE_STRICT_KEYWORD_MUTE.key.equals(key)
                        || Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key.equals(key)
                        || Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key.equals(key)
                        || Settings.TIMELINE_STRICT_KEYWORD_MUTE_CONVERSATIONS.key.equals(key)) {
                    // Never acquire the parsing lock from a synchronous preference write.
                    new Handler(Looper.getMainLooper()).post(StrictKeywordMute::reloadPreferences);
                }
            };
            preferences.preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            preferencesInitialized = true;
            reloadPreferences();
        } catch (Throwable ignored) {
            enabled = false;
            ENGINE.setRules(Collections.emptyList());
        }
    }

    private static synchronized void reloadPreferences() {
        try {
            enabled = preferences.getBoolean(Settings.TIMELINE_STRICT_KEYWORD_MUTE.key,
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE.defaultValue);
            conversationWideEnabled = preferences.getBoolean(
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE_CONVERSATIONS.key, false);
            ENGINE.setRules(KeywordMuteStore.load(preferences.preferences));
        } catch (Throwable ignored) {
            // Preserve invalid stored data for recovery; never turn it into permanent rules.
            enabled = false;
            ENGINE.setRules(Collections.emptyList());
        }
    }

    /** Called after parsing, before the surrounding timeline entry is returned. */
    public static synchronized JsonApiTweet onTweetParsed(JsonApiTweet tweet) {
        if (!SettingsStatus.strictKeywordMute) return tweet;
        if (!preferencesInitialized) initializePreferences();
        if (!enabled || tweet == null) return tweet;
        long now = System.currentTimeMillis();
        ENGINE.refresh(now, ZoneId.systemDefault());
        if (!ENGINE.hasActiveRules()) return tweet;
        Long conversationId = getConversationId(tweet);
        if (conversationWideEnabled && conversationId != null) {
            ENGINE.rememberTweetConversation(tweet.N, conversationId);
            ENGINE.rememberTweetConversation(tweet.U, conversationId);
        }
        // Keep direct keyword evidence separate from conversation inheritance.
        Set<String> matches = new HashSet<>();
        ENGINE.collectMatches(tweet.V, matches);
        // Collect every matching rule, so expiry of one does not discard other valid reasons.
        try {
            Object text = getField(getField(tweet, "O"), "l");
            if (text instanceof String) ENGINE.collectMatches((String) text, matches);
        } catch (Throwable ignored) { }
        try {
            collectTweetModel(getField(tweet, "X"), new IdentityHashMap<>(), 0, matches);
        } catch (Throwable ignored) { }
        try {
            Object builder = invokeNoArgsReturning(tweet, "com.twitter.model.core.b$a");
            collectTweetModel(invokeNoArgs(builder, "k"), new IdentityHashMap<>(), 0, matches);
        } catch (Throwable ignored) { }
        if (!matches.isEmpty()) {
            ENGINE.rememberTweet(tweet.N, matches);
            ENGINE.rememberTweet(tweet.U, matches);
            if (conversationWideEnabled && conversationId != null) {
                ENGINE.rememberConversation(conversationId, matches);
            }
        }
        return tweet;
    }

    /** Returns only the displayed outer post's conversation ID; quote/repost traversal is intentionally excluded. */
    private static Long getConversationId(JsonApiTweet jsonApiTweet) {
        try {
            Object direct = getField(jsonApiTweet, "b");
            if (direct instanceof Long && (Long) direct > 0) return (Long) direct;

            Object legacyTweet = getField(jsonApiTweet, "O");
            Object legacy = getField(legacyTweet, "b");
            return legacy instanceof Long && (Long) legacy > 0 ? (Long) legacy : null;
        } catch (Throwable ignored) {
            // Fail open for conversation-wide muting while preserving keyword matching.
            return null;
        }
    }

    /** Re-check time even when no new post was parsed at the schedule boundary. */
    public static synchronized boolean shouldRemoveEntry(String entryId) {
        if (!SettingsStatus.strictKeywordMute) return false;
        if (!preferencesInitialized) initializePreferences();
        if (!enabled || entryId == null) return false;
        long now = System.currentTimeMillis();
        ENGINE.refresh(now, ZoneId.systemDefault());
        if (!ENGINE.hasActiveRules()) return false;
        return app.morphe.extension.twitter.patches.keywordmute.MuteEntryIds.any(entryId,
                id -> ENGINE.isTweetMuted(id, now, conversationWideEnabled));
    }

    private static void collectTweetModel(Object tweet, IdentityHashMap<Object, Boolean> visited,
                                          int depth, Set<String> matches) throws Exception {
        if (tweet == null || depth > MAX_TRAVERSAL_DEPTH || visited.put(tweet, Boolean.TRUE) != null) return;
        if (!"com.twitter.model.core.b".equals(tweet.getClass().getName())) return;
        Object data = getField(tweet, "f");
        if (data != null) {
            collectTextObject(getField(data, "k"), matches);
            collectTextObject(getField(data, "l"), matches);
            Object note = getField(data, "t0");
            Object noteData = invokeNoArgs(getField(note, "c"), "getValue");
            Object text = getField(noteData, "b");
            if (text instanceof String) ENGINE.collectMatches((String) text, matches);
        }
        Object quoted = unwrapTweetResult(getField(tweet, "d"));
        collectTweetModel(quoted, visited, depth + 1, matches);
    }

    private static void collectTextObject(Object object, Set<String> matches) throws Exception {
        Object text = invokeNoArgs(object, "getText");
        if (text != null) ENGINE.collectMatches(text.toString(), matches);
    }

    private static Object unwrapTweetResult(Object result) throws Exception {
        if (result == null) return null;
        String className = result.getClass().getName();
        if ("com.twitter.model.core.b".equals(className)) return result;
        if ("com.twitter.model.core.l0".equals(className)) return getField(result, "a");
        if ("com.twitter.model.core.h0".equals(className)) {
            return invokeNoArgs(getField(result, "d"), "getValue");
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object getField(Object target, String name) throws Exception {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    private static Method findNoArgsMethod(Class<?> type, String name) {
        String key = type.getName() + '#' + name + "()";
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == 0
                        && !Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    METHOD_CACHE.put(key, method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        if (target == null) return null;
        Method method = findNoArgsMethod(target.getClass(), name);
        return method == null ? null : method.invoke(target);
    }

    private static Object invokeNoArgsReturning(Object target, String returnTypeName) throws Exception {
        if (target == null) return null;
        String key = target.getClass().getName() + "#returns:" + returnTypeName;
        Method method = METHOD_CACHE.get(key);
        if (method == null) {
            Class<?> current = target.getClass();
            while (current != null && method == null) {
                for (Method candidate : current.getDeclaredMethods()) {
                    if (candidate.getParameterTypes().length == 0
                            && candidate.getReturnType().getName().equals(returnTypeName)) {
                        candidate.setAccessible(true);
                        method = candidate;
                        METHOD_CACHE.put(key, candidate);
                        break;
                    }
                }
                current = current.getSuperclass();
            }
        }
        return method == null ? null : method.invoke(target);
    }
}
