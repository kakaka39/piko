/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import android.content.SharedPreferences;

import com.twitter.api.model.json.core.JsonApiTweet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.twitter.settings.Settings;

/** Strict substring matching for public posts parsed by X's JsonApiTweet pipeline. */
public final class StrictKeywordMute {
    private static final int MAX_TRAVERSAL_DEPTH = 4;
    private static final int MAX_MUTED_TWEET_IDS = 4096;
    private static final int MAX_MUTED_CONVERSATION_IDS = 4096;

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> MUTED_TWEET_IDS = Collections.synchronizedMap(
            new LinkedHashMap<Long, Boolean>(MAX_MUTED_TWEET_IDS + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > MAX_MUTED_TWEET_IDS;
                }
            }
    );
    private static final Map<Long, Boolean> MUTED_CONVERSATION_IDS = Collections.synchronizedMap(
            new LinkedHashMap<Long, Boolean>(MAX_MUTED_CONVERSATION_IDS + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > MAX_MUTED_CONVERSATION_IDS;
                }
            }
    );

    private static volatile boolean enabled;
    private static volatile boolean conversationWideEnabled;
    private static volatile boolean preferencesInitialized;
    private static volatile List<String> keywords = Collections.emptyList();
    private static PikoSharedPrefCategory preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private StrictKeywordMute() {
    }

    private static synchronized void initializePreferences() {
        if (preferencesInitialized) return;
        try {
            preferences = new PikoSharedPrefCategory(Settings.SHARED_PREF_NAME);
            reloadPreferences();
            preferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                        @Override
                        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                            if (Settings.TIMELINE_STRICT_KEYWORD_MUTE.key.equals(key)
                                    || Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key.equals(key)
                                    || Settings.TIMELINE_STRICT_KEYWORD_MUTE_CONVERSATIONS.key.equals(key)) {
                                reloadPreferences();
                            }
                        }
                    };
            preferences.preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            preferencesInitialized = true;
        } catch (Throwable ignored) {
            enabled = false;
            keywords = Collections.emptyList();
        }
    }

    private static void reloadPreferences() {
        try {
            enabled = preferences.getBoolean(
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE.key,
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE.defaultValue
            );
            conversationWideEnabled = preferences.getBoolean(
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE_CONVERSATIONS.key,
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE_CONVERSATIONS.defaultValue
            );
            if (!conversationWideEnabled) MUTED_CONVERSATION_IDS.clear();
            setKeywords(preferences.getString(
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.key,
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS.defaultValue
            ));
        } catch (Throwable ignored) {
            enabled = false;
            conversationWideEnabled = false;
            keywords = Collections.emptyList();
            MUTED_TWEET_IDS.clear();
            MUTED_CONVERSATION_IDS.clear();
        }
    }

    private static void setKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            keywords = Collections.emptyList();
            MUTED_TWEET_IDS.clear();
            MUTED_CONVERSATION_IDS.clear();
            return;
        }

        String lineNormalized = rawKeywords.replace("\r\n", "\n").replace('\r', '\n');
        Set<String> unique = new java.util.LinkedHashSet<>();
        for (String line : lineNormalized.split("\n", -1)) {
            String keyword = normalize(line);
            if (!keyword.isEmpty()) unique.add(keyword);
        }
        keywords = Collections.unmodifiableList(new ArrayList<>(unique));
        MUTED_TWEET_IDS.clear();
        MUTED_CONVERSATION_IDS.clear();
    }

    /** Called after JsonApiTweet parsing, before the surrounding timeline entry is returned. */
    public static JsonApiTweet onTweetParsed(JsonApiTweet jsonApiTweet) {
        if (!preferencesInitialized) initializePreferences();
        if (!enabled || keywords.isEmpty() || jsonApiTweet == null) return jsonApiTweet;
        Long conversationId = getConversationId(jsonApiTweet);
        if (conversationWideEnabled && conversationId != null
                && MUTED_CONVERSATION_IDS.containsKey(conversationId)) {
            rememberOuterTweetIds(jsonApiTweet);
            return jsonApiTweet;
        }
        try {
            boolean muted = matchesText(jsonApiTweet.V);
            if (!muted) {
                Object legacyTweet = getField(jsonApiTweet, "O");
                Object legacyText = getField(legacyTweet, "l");
                muted = legacyText instanceof String && matchesText((String) legacyText);
            }
            if (!muted) {
                muted = matchesTweetModel(getField(jsonApiTweet, "X"), new IdentityHashMap<>(), 0);
            }
            if (!muted) {
                Object builder = invokeNoArgsReturning(jsonApiTweet, "com.twitter.model.core.b$a");
                // ApiTweet.Builder extends a generic base class. Its DEX signature returns Object,
                // even though k() constructs com.twitter.model.core.b, so return-type lookup fails.
                Object tweet = invokeNoArgs(builder, "k");
                muted = matchesTweetModel(tweet, new IdentityHashMap<>(), 0);
            }
            if (muted) {
                rememberOuterTweetIds(jsonApiTweet);
                if (conversationWideEnabled && conversationId != null) {
                    MUTED_CONVERSATION_IDS.put(conversationId, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) {
            // Fail open: a post remains visible if X changes an internal model path.
        }
        return jsonApiTweet;
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

    private static void rememberOuterTweetIds(JsonApiTweet jsonApiTweet) {
        rememberTweetId(jsonApiTweet.N);
        rememberTweetId(jsonApiTweet.U);
    }

    /** Called by the proven-safe timeline JSON entry null-filter hook. */
    public static boolean shouldRemoveEntry(String entryId) {
        if (!enabled || keywords.isEmpty() || entryId == null || MUTED_TWEET_IDS.isEmpty()) return false;
        long value = 0;
        boolean readingDigits = false;
        for (int i = 0; i <= entryId.length(); i++) {
            char c = i < entryId.length() ? entryId.charAt(i) : '\0';
            if (c >= '0' && c <= '9') {
                readingDigits = true;
                int digit = c - '0';
                if (value > (Long.MAX_VALUE - digit) / 10) {
                    readingDigits = false;
                    value = 0;
                    continue;
                }
                value = value * 10 + digit;
            } else if (readingDigits) {
                if (MUTED_TWEET_IDS.containsKey(value)) return true;
                readingDigits = false;
                value = 0;
            }
        }
        return false;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .toLowerCase(Locale.ROOT)
                .trim();
        StringBuilder visible = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.getType(codePoint) != Character.FORMAT) {
                visible.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return visible.toString();
    }

    static boolean matchesText(String text) {
        if (text == null || text.isEmpty()) return false;
        String normalizedText = normalize(text);
        if (normalizedText.isEmpty()) return false;
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean matchesTweetModel(Object tweet, IdentityHashMap<Object, Boolean> visited, int depth)
            throws Exception {
        if (tweet == null || depth > MAX_TRAVERSAL_DEPTH || visited.put(tweet, Boolean.TRUE) != null) return false;
        if (!"com.twitter.model.core.b".equals(tweet.getClass().getName())) return false;

        Object data = getField(tweet, "f");
        if (data != null) {
            if (matchesTextObject(getField(data, "k")) || matchesTextObject(getField(data, "l"))) return true;
            if (matchesNoteTweet(getField(data, "t0"))) return true;
        }

        Object quotedResult = getField(tweet, "d");
        Object quotedTweet = unwrapTweetResult(quotedResult);
        return matchesTweetModel(quotedTweet, visited, depth + 1);
    }

    private static boolean matchesTextObject(Object textObject) throws Exception {
        if (textObject == null) return false;
        Object text = invokeNoArgs(textObject, "getText");
        return text != null && matchesText(text.toString());
    }

    private static boolean matchesNoteTweet(Object noteTweet) throws Exception {
        if (noteTweet == null) return false;
        Object lazy = getField(noteTweet, "c");
        Object noteTweetData = invokeNoArgs(lazy, "getValue");
        Object text = getField(noteTweetData, "b");
        return text instanceof String && matchesText((String) text);
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

    private static void rememberTweetId(long tweetId) {
        if (tweetId > 0) MUTED_TWEET_IDS.put(tweetId, Boolean.TRUE);
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
