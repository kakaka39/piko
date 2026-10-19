import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.patches.StrictKeywordMute;
import app.morphe.extension.twitter.patches.accountmute.AccountMute;
import app.morphe.extension.twitter.patches.keywordmute.*;
import com.twitter.api.model.json.core.JsonApiTweet;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.Collections;

/** Exercise the actual compiled entry points with existing in-memory mute evidence. */
public class PatchGuardTest {
    public static final class ConversationTweet extends JsonApiTweet {
        public long b;
        ConversationTweet(long id, long conversation, String text) {
            N = U = id; b = conversation; V = text;
        }
    }
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void set(Class<?> cls, String name, Object value) throws Exception {
        Field field = cls.getDeclaredField(name); field.setAccessible(true); field.set(null,value);
    }
    private static KeywordMuteEngine seed(Class<?> cls, String initialized) throws Exception {
        set(cls, "enabled", true); set(cls, initialized, true);
        Field field = cls.getDeclaredField("ENGINE"); field.setAccessible(true);
        KeywordMuteEngine engine = (KeywordMuteEngine)field.get(null);
        engine.setRules(Collections.singletonList(new KeywordMuteRule("test","target",true,
                KeywordMuteRule.Mode.ALWAYS,0,1,0,1,1)));
        engine.refresh(System.currentTimeMillis(), ZoneId.systemDefault());
        engine.rememberTweet(1,Collections.singleton("test")); return engine;
    }
    public static void main(String[] args) throws Exception {
        KeywordMuteEngine words = seed(StrictKeywordMute.class,"preferencesInitialized");
        KeywordMuteEngine accounts = seed(AccountMute.class,"initialized");
        Field postsField = AccountMute.class.getDeclaredField("POSTS"); postsField.setAccessible(true);
        app.morphe.extension.twitter.patches.accountmute.AccountPostCache posts =
                (app.morphe.extension.twitter.patches.accountmute.AccountPostCache)postsField.get(null);
        posts.remember(1,Collections.singleton("target"));
        posts.remember(3,Collections.singleton("target"));
        SettingsStatus.strictKeywordMute = false; SettingsStatus.accountMute = false;
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-1"),"omitted keyword patch ignores saved evidence");
        check(!AccountMute.shouldRemoveEntry("tweet-1"),"omitted account patch ignores saved evidence");
        JsonApiTweet tweet = new JsonApiTweet(); tweet.N=2; tweet.U=2; tweet.V="target";
        check(StrictKeywordMute.onTweetParsed(tweet)==tweet,"disabled keyword hook preserves input");
        check(!words.isTweetMuted(2,System.currentTimeMillis()),"disabled hook cannot add evidence");
        tweet.N=3; tweet.U=3;
        AccountMute.onTweetParsed(tweet);
        check(posts.isMuted(3,accounts) && !posts.isMuted(2,accounts),"disabled account hook preserves cached identities");
        SettingsStatus.accountMute = true;
        check(AccountMute.shouldRemoveEntry("tweet-1") && !StrictKeywordMute.shouldRemoveEntry("tweet-1"),"account-only patch independent of keyword patch");
        SettingsStatus.accountMute = false; SettingsStatus.strictKeywordMute = true;
        check(StrictKeywordMute.shouldRemoveEntry("tweet-1") && !AccountMute.shouldRemoveEntry("tweet-1"),"keyword-only patch independent of account patch");
        set(StrictKeywordMute.class,"conversationWideEnabled",true);
        StrictKeywordMute.onTweetParsed(new ConversationTweet(100,100,"clean root"));
        StrictKeywordMute.onTweetParsed(new ConversationTweet(101,100,"clean sibling"));
        StrictKeywordMute.onTweetParsed(new ConversationTweet(102,200,"unrelated"));
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-100"),"root initially passes JSON entry check");
        StrictKeywordMute.onTweetParsed(new ConversationTweet(103,100,"target reply"));
        check(StrictKeywordMute.shouldRemoveEntry("tweet-100"),"post-parse conversion rejects earlier root");
        check(StrictKeywordMute.shouldRemoveEntry("conversationthread-100-tweet-101"),"module conversion rejects earlier sibling");
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-102"),"unrelated conversation retained");
        StrictKeywordMute.onTweetParsed(new ConversationTweet(104,100,"later clean reply"));
        check(StrictKeywordMute.shouldRemoveEntry("tweet-104"),"forward parse order also inherits conversation evidence");
        set(StrictKeywordMute.class,"conversationWideEnabled",false);
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-100") && !StrictKeywordMute.shouldRemoveEntry("tweet-104"),
                "conversation evidence never becomes a direct keyword match");
        check(StrictKeywordMute.shouldRemoveEntry("tweet-103"),"direct reply match survives conversation switch");
        SettingsStatus.strictKeywordMute = false;
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-103"),"conversion check respects omitted patch");
        SettingsStatus.strictKeywordMute = true;
        set(StrictKeywordMute.class,"enabled",false); set(AccountMute.class,"enabled",false);
        SettingsStatus.accountMute = true;
        check(!StrictKeywordMute.shouldRemoveEntry("tweet-1") && !AccountMute.shouldRemoveEntry("tweet-1"),"master toggles ignore cached evidence");
        System.out.println("PASS: "+checks+" compiled patch guard checks");
    }
}
