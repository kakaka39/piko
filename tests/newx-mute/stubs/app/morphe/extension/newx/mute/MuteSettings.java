package app.morphe.extension.newx.mute;

/** Avoid Android resource initialization in standalone storage tests. */
public final class MuteSettings {
    public static final String SHARED_PREF_NAME = "test";
    public static boolean keywords, accounts, conversation;
    public static boolean enabled(boolean account) { return account ? accounts : keywords; }
    public static boolean conversations() { return conversation; }
    public static final Key ACCOUNT_MUTE_RULES = new Key("account_mute_rules");
    public static final Key TIMELINE_STRICT_KEYWORD_MUTE_RULES = new Key("timeline_strict_keyword_mute_rules");
    public static final Key TIMELINE_STRICT_KEYWORD_MUTE_KEYWORDS = new Key("timeline_strict_keyword_mute_keywords");
    public static final class Key {
        public void save(String value) {}
        public final String key;
        Key(String key) { this.key = key; }
    }
}
