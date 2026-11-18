package app.morphe.extension.newx.timeline;

import android.content.SharedPreferences;
import app.morphe.extension.newx.mute.MuteModel.Post;
import app.morphe.extension.newx.mute.MuteSettings;
import app.morphe.extension.newx.mute.keywordmute.*;
import app.morphe.extension.newx.mute.accountmute.*;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import java.util.*;

public final class ScheduledMuteTest {
    private static final class Memory implements SharedPreferences {
        final Map<String, String> data = new HashMap<>();
        public boolean contains(String key) { return data.containsKey(key); }
        public String getString(String key, String fallback) { return data.getOrDefault(key, fallback); }
        public Editor edit() { return new Editor() {
            public Editor putString(String key, String value) { data.put(key, value); return this; }
            public Editor remove(String key) { data.remove(key); return this; }
            public void apply() {}
        }; }
    }
    private static final TimelineModelAccess ACCESS = new TimelineModelAccess() {
        @Override boolean isPost(Object value) { return value instanceof Post; }
    };
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static KeywordMuteRule rule(String word) {
        return new KeywordMuteRule(UUID.randomUUID().toString(), word, true,
                KeywordMuteRule.Mode.ALWAYS, 0, 1, 0, 1, 1);
    }
    public static void main(String[] args) throws Exception {
        Memory prefs = new Memory(); PikoSharedPrefCategory.current = prefs;
        MuteSettings.keywords = true; MuteSettings.accounts = false; MuteSettings.conversation = true;
        KeywordMuteStore.save(prefs, Arrays.asList(rule("blocked")));
        Post root = new Post(1, 0, "clean root", "alice");
        Post sibling = new Post(11, 1, "clean reply", "bob");
        Post match = new Post(12, 1, "BLOCKED reply", "carol");
        Post unrelated = new Post(2, 0, "clean unrelated", "dave");
        List<Post> items = Arrays.asList(root, sibling, match, unrelated);
        ScheduledMute.withPass(items, ACCESS, () -> {
            check(ScheduledMute.shouldRemove(root), "later matching reply hides earlier root");
            check(ScheduledMute.shouldRemove(sibling), "same conversation sibling is hidden");
            check(ScheduledMute.shouldRemove(match), "direct match is hidden");
            check(!ScheduledMute.shouldRemove(unrelated), "unrelated conversation survives");
            return items;
        });
        check(!ScheduledMute.active(), "pass context is cleared");
        MuteSettings.conversation = false;
        ScheduledMute.withPass(items, ACCESS, () -> {
            check(!ScheduledMute.shouldRemove(root) && ScheduledMute.shouldRemove(match), "conversation toggle is immediate");
            return items;
        });
        root.note = "blocked in long text";
        ScheduledMute.withPass(items, ACCESS, () -> {
            check(ScheduledMute.shouldRemove(root), "long note text is checked"); return items;
        });
        MuteSettings.keywords = false; MuteSettings.accounts = true;
        prefs.data.put(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "{broken");
        AccountMuteStore.save(prefs, Arrays.asList(rule("bob")));
        Post quote = new Post(5, 0, "clean", "notbob"); quote.quote = sibling;
        Post repost = new Post(6, 0, "clean", "notbob"); repost.repost = quote;
        ScheduledMute.withPass(Arrays.asList(root, sibling, unrelated, quote, repost), ACCESS, () -> {
            check(ScheduledMute.shouldRemove(sibling), "exact author matched");
            check(ScheduledMute.shouldRemove(quote), "quoted author matched");
            check(ScheduledMute.shouldRemove(repost), "nested quoted/reposted author matched");
            check(!ScheduledMute.shouldRemove(unrelated), "other author survives"); return null;
        });
        MuteSettings.accounts = false;
        ScheduledMute.withPass(items, ACCESS, () -> { check(!ScheduledMute.active(), "omitted or disabled patches do not filter"); return null; });
        MuteSettings.accounts = true;
        int[] calls = {0};
        try {
            ScheduledMute.withPass(items, ACCESS, () -> { calls[0]++; throw new IllegalStateException("expected"); });
        } catch (IllegalStateException expected) { }
        check(calls[0] == 1 && !ScheduledMute.active(), "downstream failures neither retry filtering nor leak context");
        System.out.println("NewX timeline mute integration regressions passed");
    }
}
