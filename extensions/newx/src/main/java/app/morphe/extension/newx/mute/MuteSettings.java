package app.morphe.extension.newx.mute;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.StringSetting;

/** Rule values share the NewX backup preferences; patch presence is checked through the registry. */
public final class MuteSettings {
    public static final String SHARED_PREF_NAME = app.morphe.extension.shared.settings.Setting.preferences.name;
    public static final String KEYWORDS_ENABLED = "newx.custom.keyword_mute.enabled";
    public static final String ACCOUNTS_ENABLED = "newx.custom.account_mute.enabled";
    public static final String CONVERSATIONS = "newx.custom.keyword_mute.conversations";
    public static final StringSetting ACCOUNT_MUTE_RULES = new StringSetting("account_mute_rules", "");
    public static final StringSetting TIMELINE_STRICT_KEYWORD_MUTE_RULES = new StringSetting("timeline_strict_keyword_mute_rules", "");
    public static final StringSetting TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS = new StringSetting("timeline_strict_keyword_mute_keywords", "");

    private MuteSettings() {}

    public static boolean enabled(boolean accounts) {
        String key = accounts ? ACCOUNTS_ENABLED : KEYWORDS_ENABLED;
        return SettingsRegistry.isRegistered(key) && SettingsRegistry.getBooleanOrDefault(key, true);
    }

    public static boolean conversations() {
        return SettingsRegistry.isRegistered(CONVERSATIONS)
                && SettingsRegistry.getBooleanOrDefault(CONVERSATIONS, false);
    }

    /** Calling this initializes the StringSettings above before the shared backup is exported. */
    public static void initializeForBackup() {}
}
