package android.content;

/** Only the Android API surface exercised by the real storage implementation. */
public interface SharedPreferences {
    boolean contains(String key);
    String getString(String key, String fallback);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor remove(String key);
        void apply();
    }
}
