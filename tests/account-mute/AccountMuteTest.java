import app.morphe.extension.twitter.patches.accountmute.*;
import app.morphe.extension.twitter.patches.keywordmute.*;
import android.content.SharedPreferences;
import com.twitter.model.core.*;
import com.twitter.model.core.entity.*;
import java.time.*;
import java.util.*;
import org.json.JSONObject;

public class AccountMuteTest {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++; if (!value) throw new AssertionError(name);
    }
    public static class JsonTweet {
        public Object W, R, X, T, S;
        public String text;
        public b model;
        public b.a build() { return new b.a(model); }
    }
    public static class Core { public Object a, b; }
    public static class Result { public Object a; }
    public static class Info { public Object I1; }
    public static class Repost { public String c; public Repost(String handle) { c = handle; } }
    private static b post(String handle) { b tweet = new b(); tweet.b = new k1(handle); return tweet; }
    private static KeywordMuteRule rule(String id, String handle) {
        return new KeywordMuteRule(id, handle, true, KeywordMuteRule.Mode.ALWAYS, 0, 1, 0, 1, 1);
    }
    private static final class Memory implements SharedPreferences {
        Map<String,String> values = new HashMap<>(); int writes;
        public boolean contains(String k) { return values.containsKey(k); }
        public String getString(String k,String fallback) { return values.getOrDefault(k,fallback); }
        public Editor edit() { return new Editor() {
            Map<String,String> pending = new HashMap<>();
            public Editor putString(String k,String v) { pending.put(k,v); return this; }
            public Editor remove(String k) { pending.put(k,null); return this; }
            public void apply() { pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);}); writes++; }
        }; }
    }
    public static void main(String[] args) throws Exception {
        AccountTargets.Batch batch = AccountTargets.parse("@Alice\r\nBOB,alice 、 _other; C_3　d4");
        check(batch.handles.equals(Arrays.asList("alice","bob","_other","c_3","d4")), "ordered case-folded bulk input");
        check(batch.duplicates==1 && batch.invalid.isEmpty(), "deduplicate bulk input");
        check(AccountTargets.parse("@@alice name-with-dash \u200balice 表示名").invalid.size()==4, "reject malformed handles and invisible characters");
        check(AccountTargets.parse(" \n,;　").handles.isEmpty(), "empty batch");
        List<KeywordMuteRule> grouped = AccountMuteStore.withAccounts(Collections.emptyList(),
                Arrays.asList("alice", "bob", "carol"), rule("period", "alice"), null);
        check(AccountMuteStore.groups(grouped).size()==1, "multi-line creation stays in one display group");
        Memory groupedMemory = new Memory();
        AccountMuteStore.save(groupedMemory, grouped);
        grouped = AccountMuteStore.load(groupedMemory);
        check(AccountMuteStore.groups(grouped).get(0).stream().map(r->r.keyword).collect(java.util.stream.Collectors.toList())
                .equals(Arrays.asList("alice", "bob", "carol")), "group and line order survive persistence");
        List<KeywordMuteRule> extra = AccountMuteStore.withAccounts(grouped, Arrays.asList("alice", "bob"), rule("p", "alice"), null);
        check(AccountMuteStore.groups(extra).size()==2, "separate additions with identical periods remain separate");
        Set<String> groupIds = new HashSet<>();
        for (KeywordMuteRule r : grouped) groupIds.add(r.id);
        List<KeywordMuteRule> reordered = AccountMuteStore.withSchedules(grouped, Arrays.asList("carol", "bob", "alice"),
                rule("p", "alice"), groupIds);
        check(reordered.stream().map(r->r.keyword).collect(java.util.stream.Collectors.toList())
                .equals(Arrays.asList("carol", "bob", "alice")), "editing retains entered line order");
        List<KeywordMuteRule> groupEdit = AccountMuteStore.withSchedules(extra, Arrays.asList("bob", "dave"),
                new KeywordMuteRule("p", "bob", true, KeywordMuteRule.Mode.WEEKLY, 0, 1, 60, 2, 120), groupIds);
        check(AccountMuteStore.groups(groupEdit).size()==2 && groupEdit.size()==4, "editing a group replaces its membership only");
        check(AccountMuteStore.groups(groupEdit).get(0).stream().map(r->r.keyword).collect(java.util.stream.Collectors.toList())
                .equals(Arrays.asList("bob", "dave")), "edited group retains new lines and shared schedule");
        groupEdit.removeIf(r->r.groupId.equals(extra.get(0).groupId));
        check(groupEdit.size()==2 && AccountMuteStore.groups(groupEdit).size()==1, "group deletion retains separate schedules for same accounts");
        List<KeywordMuteRule> legacyGroups = AccountMuteStore.decode(new JSONObject(KeywordMuteStore.encode(extra, "account"))
                .put("version", 2).toString());
        check(AccountMuteStore.groups(legacyGroups).size()==extra.size(), "old independent rows are never inferred to be one batch");
        List<KeywordMuteRule> restoredGroups = AccountMuteStore.rulesForImport(new JSONObject()
                .put("account_mute_rules", groupedMemory.values.get("account_mute_rules")));
        check(AccountMuteStore.groups(restoredGroups).size()==1, "backup restore preserves batch grouping");
        JsonTweet json = new JsonTweet(); json.W = new k1("Author");
        check(AccountExtractor.fromJsonTweet(json).equals(Collections.singleton("author")), "REST author");
        json = new JsonTweet(); Core core = new Core(); Result result = new Result(); result.a = new t1(new k1("graphql")); core.b = result; json.R = core;
        check(AccountExtractor.fromJsonTweet(json).contains("graphql"), "GraphQL user_results author");
        core.b = null; core.a = new t1(new k1("singular"));
        check(AccountExtractor.fromJsonTweet(json).contains("singular"), "GraphQL user_result author");
        json = new JsonTweet(); json.W = new k1("reposter"); json.X = post("original");
        check(AccountExtractor.fromJsonTweet(json).equals(new HashSet<>(Arrays.asList("reposter","original"))), "reposter and original author");
        json = new JsonTweet(); json.model = post("outer"); Info info = new Info(); info.I1 = new Repost("actor"); json.model.f = info;
        json.model.d = new l0(post("quoted"));
        Set<String> found = AccountExtractor.fromJsonTweet(json);
        check(found.containsAll(Arrays.asList("outer","actor","quoted")), "built model carries reposter and quote author");
        ((l0)json.model.d).a.d = new h0(post("nested"));
        check(AccountExtractor.fromJsonTweet(json).contains("nested"), "nested lazy quote");
        ((l0)json.model.d).a.d = new l0(json.model);
        check(AccountExtractor.fromJsonTweet(json).size()==3, "cycles terminate safely");
        json = new JsonTweet(); json.text = "@target is mentioned"; json.W = new k1("someone");
        check(!AccountExtractor.fromJsonTweet(json).contains("target"), "text mentions are not authors");
        check(AccountExtractor.fromJsonTweet(new Object()).isEmpty(), "unknown layout fails open");
        KeywordMuteEngine engine = new KeywordMuteEngine(); engine.setRules(Collections.singletonList(rule("id","alice")));
        long now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli(); engine.refresh(now, ZoneId.of("Asia/Tokyo"));
        Set<String> matched = new HashSet<>(); engine.collectExactMatches("alice_extra", matched);
        check(matched.isEmpty(), "no account substring matches"); engine.collectExactMatches("alice",matched);
        check(matched.equals(Collections.singleton("id")), "exact account match");
        KeywordMuteRule timed = new KeywordMuteRule("timed","bob",true,KeywordMuteRule.Mode.UNTIL,
                now+60000,1,0,1,1);
        engine.setRules(Arrays.asList(rule("keep","alice"),timed)); engine.refresh(now,ZoneId.of("UTC"));
        Set<String> reasons = new HashSet<>(); engine.collectExactMatches("alice",reasons);
        engine.collectExactMatches("bob",reasons); engine.rememberTweet(20,reasons);
        engine.rememberTweet(21,Collections.singleton("timed"));
        engine.refresh(now+60000,ZoneId.of("UTC"));
        check(engine.isTweetMuted(20,now+60000) && !engine.isTweetMuted(21,now+60000),"account expiry removes only expired evidence");
        KeywordMuteRule weekly = new KeywordMuteRule("weekly","bob",true,KeywordMuteRule.Mode.WEEKLY,
                0,1,720,1,721);
        engine.setRules(Collections.singletonList(weekly)); engine.refresh(now,ZoneId.of("UTC"));
        Set<String> weeklyMatches = new HashSet<>(); engine.collectExactMatches("bob",weeklyMatches);
        check(weeklyMatches.contains("weekly"),"weekly account active at inclusive start");
        engine.rememberTweet(22,weeklyMatches); engine.refresh(now+7*86400000L,ZoneId.of("UTC"));
        check(!engine.isTweetMuted(22,now+7*86400000L),"old account evidence not reused next week");
        KeywordMuteRule period = new KeywordMuteRule("template","alice",true,KeywordMuteRule.Mode.WEEKLY,0,7,1380,1,30);
        List<KeywordMuteRule> current = Arrays.asList(rule("keep","untouched"),rule("existing","alice"));
        List<KeywordMuteRule> updated = AccountMuteStore.withAccounts(current,Arrays.asList("alice","bob"),period,"existing");
        check(updated.size()==3 && updated.get(0)==current.get(0), "batch retains unrelated rules");
        check(updated.get(1).id.equals("existing") && updated.get(2).mode==KeywordMuteRule.Mode.WEEKLY, "batch updates and adds with same period");
        check(current.get(1).mode==KeywordMuteRule.Mode.ALWAYS, "batch does not mutate input");
        check(AccountMuteStore.withAccounts(updated,Collections.singletonList("renamed"),period,"existing").stream().noneMatch(r->r.keyword.equals("alice")), "single account rename");
        Memory memory = new Memory(); KeywordMuteStore.save(memory,Collections.singletonList(rule("word","word")));
        AccountMuteStore.save(memory,updated);
        check(AccountMuteStore.load(memory).size()==3 && KeywordMuteStore.load(memory).get(0).keyword.equals("word"), "independent account and keyword storage");
        List<KeywordMuteRule> appended = AccountMuteStore.withAccounts(updated, Collections.singletonList("alice"), period, null);
        check(appended.size()==4 && appended.get(1).id.equals("existing"), "append preserves existing account schedules");
        check(!appended.get(3).id.equals("existing"), "same account schedules have independent IDs");
        AccountMuteStore.save(memory,appended);
        List<KeywordMuteRule> multiple = AccountMuteStore.load(memory);
        check(multiple.size()==4 && multiple.stream().filter(r->r.keyword.equals("alice")).count()==2, "multiple schedules roundtrip");
        check(new JSONObject(memory.values.get("account_mute_rules")).getInt("version")==2, "multi-schedule schema version");
        check(AccountMuteStore.decode(KeywordMuteStore.encode(updated,"account")).size()==3, "previous single-schedule format loads");
        List<KeywordMuteRule> editedOne = AccountMuteStore.withAccounts(multiple,Collections.singletonList("alice"),rule("template","alice"),"existing");
        check(editedOne.get(1).mode==KeywordMuteRule.Mode.ALWAYS && editedOne.get(3).mode==KeywordMuteRule.Mode.WEEKLY,
                "edit only the selected schedule for an account");
        List<KeywordMuteRule> editedBoth = AccountMuteStore.withSchedules(multiple, Collections.singletonList("alice"),rule("template","alice"),
                new HashSet<>(Arrays.asList("existing",multiple.get(3).id)));
        check(editedBoth.size()==4 && editedBoth.get(1).mode==KeywordMuteRule.Mode.ALWAYS && editedBoth.get(3).mode==KeywordMuteRule.Mode.ALWAYS,
                "bulk edit can select multiple schedules for the same account");
        List<KeywordMuteRule> deleteOne = new ArrayList<>(multiple); deleteOne.removeIf(r->r.id.equals("existing"));
        AccountMuteStore.save(memory,deleteOne);
        check(AccountMuteStore.load(memory).stream().filter(r->r.keyword.equals("alice")).count()==1,"delete one schedule preserves the other");
        try { AccountMuteStore.withAccounts(deleteOne,Collections.singletonList("alice"),period,"existing"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true,"stale edit never creates a new schedule"); }
        // Two simultaneous schedules for the same handle must retain independent expiry evidence.
        KeywordMuteRule first = new KeywordMuteRule("first","alice",true,KeywordMuteRule.Mode.UNTIL,now+60000,1,0,1,1);
        KeywordMuteRule second = new KeywordMuteRule("second","alice",true,KeywordMuteRule.Mode.UNTIL,now+120000,1,0,1,1);
        engine.setRules(Arrays.asList(first,second)); engine.refresh(now,ZoneId.of("UTC"));
        Set<String> overlapping = new HashSet<>(); engine.collectExactMatches("alice",overlapping);
        check(overlapping.size()==2,"all active schedules for the same handle contribute evidence");
        engine.rememberTweet(23,overlapping); engine.refresh(now+60000,ZoneId.of("UTC"));
        check(engine.isTweetMuted(23,now+60000),"first schedule expiry keeps second schedule active");
        engine.refresh(now+120000,ZoneId.of("UTC"));
        check(!engine.isTweetMuted(23,now+120000),"unmute when all schedules have expired");
        KeywordMuteRule mon = new KeywordMuteRule("mon","alice",true,KeywordMuteRule.Mode.WEEKLY,0,1,720,1,721);
        KeywordMuteRule tue = new KeywordMuteRule("tue","alice",true,KeywordMuteRule.Mode.WEEKLY,0,2,720,2,721);
        engine.setRules(Arrays.asList(mon,tue)); engine.refresh(now,ZoneId.of("UTC"));
        Set<String> mondayMatches = new HashSet<>(); engine.collectExactMatches("alice",mondayMatches);
        check(mondayMatches.equals(Collections.singleton("mon")),"first weekly schedule active");
        engine.refresh(now+60000,ZoneId.of("UTC")); check(!engine.hasActiveRules(),"gap between weekly schedules");
        engine.refresh(now+86400000,ZoneId.of("UTC"));
        Set<String> tuesdayMatches = new HashSet<>(); engine.collectExactMatches("alice",tuesdayMatches);
        check(tuesdayMatches.equals(Collections.singleton("tue")),"second weekly schedule activates independently");
        AccountPostCache posts = new AccountPostCache(); posts.remember(24,Collections.singleton("alice"));
        KeywordMuteRule late = new KeywordMuteRule("late","alice",true,KeywordMuteRule.Mode.WEEKLY,0,1,721,1,723);
        KeywordMuteRule early = new KeywordMuteRule("early","alice",true,KeywordMuteRule.Mode.WEEKLY,0,1,720,1,722);
        engine.setRules(Arrays.asList(early,late)); engine.refresh(now,ZoneId.of("UTC"));
        check(posts.isMuted(24,engine),"known author muted in first window");
        engine.refresh(now+120000,ZoneId.of("UTC"));
        check(posts.isMuted(24,engine),"later starting overlap keeps known post muted after first ends");
        engine.refresh(now+180000,ZoneId.of("UTC"));
        check(!posts.isMuted(24,engine),"known post visible after all windows end");
        engine.setRules(Arrays.asList(mon,tue)); engine.refresh(now+3600000,ZoneId.of("UTC"));
        posts.remember(25,Collections.singleton("alice")); check(!posts.isMuted(25,engine),"post seen while schedules inactive");
        engine.refresh(now+86400000,ZoneId.of("UTC"));
        check(posts.isMuted(25,engine),"newly active window applies to previously observed author");
        engine.setRules(Collections.emptyList()); engine.refresh(now,ZoneId.of("UTC"));
        check(!posts.isMuted(24,engine),"removing schedules unmutes known posts");
        engine.setRules(Collections.singletonList(rule("permanent","alice"))); engine.refresh(now,ZoneId.of("UTC"));
        for(int i=100;i<4197;i++)posts.remember(i,Collections.singleton("alice"));
        check(!posts.isMuted(100,engine) && posts.isMuted(4196,engine),"bounded author cache");
        // Restore this fixture for the original backup assertions below.
        AccountMuteStore.save(memory,updated);
        String encoded = memory.values.get("account_mute_rules");
        check(encoded.contains("\"account\"") && !encoded.contains("\"keyword\""), "account schema");
        List<KeywordMuteRule> restored = AccountMuteStore.rulesForImport(new JSONObject().put("account_mute_rules",encoded));
        check(restored.get(2).startDay==7 && restored.get(2).endMinute==30, "account backup roundtrip");
        check(AccountMuteStore.rulesForImport(new JSONObject())==null, "old backups do not overwrite accounts");
        int writes = memory.writes;
        try { AccountMuteStore.save(memory,Collections.singletonList(rule("invalid","bad-name"))); throw new AssertionError(); }
        catch (org.json.JSONException expected) { check(memory.writes==writes, "invalid batch makes no write"); }
        check(MuteEntryIds.any("tweet-123",n->n==123),"entry ID match");
        check(!MuteEntryIds.any("tweet-999999999999999999999123",n->n==123),"overflow cannot match suffix");
        check(KeywordMuteRule.normalize("\u200b \u200b").isEmpty(),"post-format-removal whitespace rejected");
        System.out.println("PASS: "+checks+" account parsing, extraction, batch and backup checks");
    }
}
