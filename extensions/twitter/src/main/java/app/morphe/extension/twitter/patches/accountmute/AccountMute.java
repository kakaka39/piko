/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.accountmute;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.twitter.api.model.json.core.JsonApiTweet;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteEngine;
import app.morphe.extension.twitter.patches.keywordmute.MuteEntryIds;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.SettingsStatus;

/** Independent account rules: never expand account matches to unrelated conversation posts. */
public final class AccountMute {
    private static final KeywordMuteEngine ENGINE = new KeywordMuteEngine();
    private static final AccountPostCache POSTS = new AccountPostCache();
    private static PikoSharedPrefCategory preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static boolean initialized, enabled;
    private AccountMute() { }
    private static void initialize() {
        if (initialized) return;
        preferences = new PikoSharedPrefCategory(Settings.SHARED_PREF_NAME);
        listener = (shared, key) -> {
            if (key == null || Settings.ACCOUNT_MUTE.key.equals(key) || Settings.ACCOUNT_MUTE_RULES.key.equals(key)) {
                new Handler(Looper.getMainLooper()).post(AccountMute::reload);
            }
        };
        preferences.preferences.registerOnSharedPreferenceChangeListener(listener);
        initialized = true;
        reload();
    }
    private static synchronized void reload() {
        try {
            enabled = preferences.getBoolean(Settings.ACCOUNT_MUTE.key, true);
            ENGINE.setRules(AccountMuteStore.load(preferences.preferences));
        } catch (Exception ignored) {
            enabled = false; ENGINE.setRules(Collections.emptyList());
        }
    }
    public static synchronized void onTweetParsed(JsonApiTweet tweet) {
        if (!SettingsStatus.accountMute || tweet == null) return;
        try {
            initialize();
            if (!enabled) return;
            long now = System.currentTimeMillis();
            ENGINE.refresh(now, ZoneId.systemDefault());
            if (!ENGINE.hasRules()) return;
            Set<String> handles = AccountExtractor.fromJsonTweet(tweet);
            POSTS.remember(tweet.N, handles);
            POSTS.remember(tweet.U, handles);
        } catch (Exception ignored) { /* Unknown model paths fail open. */ }
    }
    public static synchronized boolean shouldRemoveEntry(String entryId) {
        if (!SettingsStatus.accountMute || entryId == null) return false;
        try {
            initialize();
            if (!enabled) return false;
            long now = System.currentTimeMillis();
            ENGINE.refresh(now, ZoneId.systemDefault());
            return MuteEntryIds.any(entryId, id -> POSTS.isMuted(id, ENGINE));
        } catch (Exception ignored) { return false; }
    }
}
