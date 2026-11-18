import app.morphe.extension.newx.mute.accountmute.*;
import app.morphe.extension.newx.mute.keywordmute.*;
import java.util.*;
import org.json.JSONObject;

public class AccountStoreTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        KeywordMuteRule period = new KeywordMuteRule("p", "placeholder", true,
                KeywordMuteRule.Mode.UNTIL, (System.currentTimeMillis() / 60000 + 60) * 60000, 1, 0, 1, 1);
        List<KeywordMuteRule> rules = AccountMuteStore.withAccounts(Collections.emptyList(),
                Arrays.asList("alice", "bob"), period, null);
        rules = AccountMuteStore.withAccounts(rules, Arrays.asList("alice"), period, null);
        check(rules.size() == 3, "duplicate handles retain independent schedules");
        check(AccountMuteStore.groups(rules).size() == 2, "separate additions remain separate groups");
        KeywordMuteStoreTest.MemoryPreferences prefs = new KeywordMuteStoreTest.MemoryPreferences();
        AccountMuteStore.save(prefs, rules);
        rules = AccountMuteStore.load(prefs);
        check(AccountMuteStore.groups(rules).size() == 2, "groups survive storage roundtrip");
        Set<String> first = new HashSet<>(Arrays.asList(rules.get(0).id, rules.get(1).id));
        List<KeywordMuteRule> edited = AccountMuteStore.withSchedules(rules, Arrays.asList("bob", "carol"), period, first);
        check(edited.size() == 3 && edited.stream().anyMatch(r -> r.keyword.equals("alice")),
                "editing one group preserves the independent duplicate handle");
        check(AccountMuteStore.groups(edited).size() == 2, "editing preserves group identity");
        JSONObject root = new JSONObject(prefs.data.get("account_mute_rules"));
        root.getJSONArray("rules").getJSONObject(0).remove("groupId");
        check(AccountMuteStore.decode(root.toString()).size() == 3, "old v2 rules without group IDs remain readable");
        check(AccountTargets.normalize("@Alice").equals("alice"), "case-insensitive exact identifier normalization");
        KeywordMuteEngine engine = new KeywordMuteEngine();
        engine.setRules(rules); engine.refresh(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        Set<String> matches = new HashSet<>();
        engine.collectExactMatches("malice", matches);
        check(matches.isEmpty(), "account names never match substrings");
        engine.collectExactMatches("alice", matches);
        check(matches.size() == 2, "overlapping schedules both contribute evidence");
        System.out.println("Account store regressions passed");
    }
}
