package app.morphe.extension.shared.settings.preference;
import android.content.SharedPreferences;
public final class PikoSharedPrefCategory {
    public static SharedPreferences current;
    public final SharedPreferences preferences;
    public PikoSharedPrefCategory(String name) { preferences = current; }
}
