package app.morphe.extension.twitter.settings;

/** Avoid Android resource initialization in standalone storage tests. */
public final class Settings {
    public static final Key ACCOUNT_MUTE_RULES = new Key("account_mute_rules");
    public static final Key TIMELINE_STRICT_KEYWORD_MUTE_RULES = new Key("timeline_strict_keyword_mute_rules");
    public static final Key TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS = new Key("timeline_strict_keyword_mute_keywords");
    public static final class Key {
        public final String key;
        Key(String key) { this.key = key; }
    }
}
