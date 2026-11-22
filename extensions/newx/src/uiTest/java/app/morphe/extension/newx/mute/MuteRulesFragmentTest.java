package app.morphe.extension.newx.mute;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.newx.mute.accountmute.AccountMuteStore;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteRule;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteStore;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Exercise real NewX views against existing storage, including account group schedules. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
@SuppressWarnings("deprecation")
public final class MuteRulesFragmentTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private SharedPreferences preferences;
    private MuteRulesFragment fragment;

    @Before public void setUp() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        Utils.setContext(activity);
        preferences = new PikoSharedPrefCategory(MuteSettings.SHARED_PREF_NAME).preferences;
        preferences.edit().clear().commit();
        if (!SettingsRegistry.isRegistered("test.mute")) {
            SettingsRegistry.registerCategory("test.mute", "test.mute.title", null, null, 0);
        }
        for (String key : List.of(MuteSettings.KEYWORDS_ENABLED, MuteSettings.ACCOUNTS_ENABLED,
                MuteSettings.CONVERSATIONS)) {
            if (!SettingsRegistry.isRegistered(key)) {
                SettingsRegistry.registerToggle("test.mute", key, key, null, 0);
                SettingsRegistry.configureToggle(key, !key.equals(MuteSettings.CONVERSATIONS), false);
            }
            BooleanSetting setting = new BooleanSetting(key, !key.equals(MuteSettings.CONVERSATIONS), false);
            settings().put(key, setting);
            setting.save(!key.equals(MuteSettings.CONVERSATIONS));
        }
        // The extension strings are injected into the APK by the patch, not the test APK.
        Field strings = StringRef.class.getDeclaredField("strings");
        strings.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, StringRef> cache = (Map<String, StringRef>) strings.get(null);
        for (String key : List.of("piko_mute_save", "piko_mute_delete", "piko_mute_add", "piko_account_mute_add",
                "piko_mute_keyword_error", "piko_mute_selected", "piko_mute_all", "piko_mute_edit_schedules")) {
            cache.put(key, StringRef.constant(key));
        }
        preferences.edit().putString("newx.content.post_filtering.rules", "independent-post-filter-rules").commit();
    }

    @After public void tearDown() throws Exception {
        controller.pause().stop().destroy();
        for (String key : List.of(MuteSettings.KEYWORDS_ENABLED, MuteSettings.ACCOUNTS_ENABLED,
                MuteSettings.CONVERSATIONS)) settings().remove(key);
    }

    @Test public void keywordScreenIsAnInlineListAndSwitchPreservesItsSchedule() throws Exception {
        KeywordMuteRule original = weekly("keyword", "test phrase", "keyword");
        KeywordMuteStore.save(preferences, List.of(original));
        open(new KeywordMuteFragment());
        assertTrue(MuteSettings.enabled(false));
        assertTrue(dialogs().isEmpty());
        View row = ruleRow(0);
        find(row, SettingsUi.SwitchControl.class).toggle(false);
        KeywordMuteRule saved = KeywordMuteStore.load(preferences).get(0);
        assertFalse(saved.enabled);
        assertEquals(original.id, saved.id);
        assertEquals(original.startDay, saved.startDay);
        assertEquals(original.endMinute, saved.endMinute);
        assertEquals(KeywordMuteRule.Mode.WEEKLY, saved.mode);
        assertEquals("independent-post-filter-rules", preferences.getString("newx.content.post_filtering.rules", ""));
    }

    @Test public void accountSwitchOnlyChangesTheSelectedCreationGroup() throws Exception {
        AccountMuteStore.save(preferences, List.of(weekly("a", "alice", "batch"),
                weekly("b", "bob", "batch"), weekly("c", "carol", "another-batch")));
        open(new AccountMuteFragment());
        assertTrue(MuteSettings.enabled(true));
        assertEquals(2, find(fragment.getView(), ListView.class).getAdapter().getCount());
        find(ruleRow(0), SettingsUi.SwitchControl.class).toggle(false);
        List<KeywordMuteRule> saved = AccountMuteStore.load(preferences);
        assertFalse(saved.get(0).enabled);
        assertFalse(saved.get(1).enabled);
        assertTrue(saved.get(2).enabled);
        assertEquals("batch", saved.get(0).groupId);
        assertEquals(600, saved.get(1).startMinute);
    }

    @Test public void masterSwitchDisablesActionsWithoutErasingRules() throws Exception {
        KeywordMuteStore.save(preferences, List.of(weekly("a", "phrase", "a")));
        String before = preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "");
        open(new KeywordMuteFragment());
        find(fragment.getView(), SettingsUi.SwitchControl.class).toggle(false);
        assertFalse(MuteSettings.enabled(false));
        assertFalse(ruleRow(0).isEnabled());
        assertEquals(before, preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, ""));
    }

    @Test public void editorRejectsBlankAndPreservesDisabledRuleUntilSave() throws Exception {
        KeywordMuteStore.save(preferences, List.of(disabled(weekly("a", "phrase", "a"))));
        open(new KeywordMuteFragment());
        ruleRow(0).performClick();
        Dialog editor = dialogs().get(0);
        assertNull(find(editor.getWindow().getDecorView(), SettingsUi.SwitchControl.class));
        EditText input = find(editor.getWindow().getDecorView(), EditText.class);
        input.setText("");
        click(editor, "piko_mute_save");
        assertTrue(editor.isShowing());
        assertEquals("phrase", KeywordMuteStore.load(preferences).get(0).keyword);
        input.setText("updated phrase");
        click(editor, "piko_mute_save");
        assertFalse(editor.isShowing());
        assertEquals("updated phrase", KeywordMuteStore.load(preferences).get(0).keyword);
        assertFalse(KeywordMuteStore.load(preferences).get(0).enabled);
        assertEquals(KeywordMuteRule.Mode.WEEKLY, KeywordMuteStore.load(preferences).get(0).mode);
    }

    @Test public void accountEditorRetainsBatchInputAndExistingSchedules() throws Exception {
        AccountMuteStore.save(preferences, List.of(weekly("a", "alice", "original")));
        open(new AccountMuteFragment());
        View fab = field(manager(), "addButton");
        fab.performClick();
        Dialog editor = dialogs().get(0);
        find(editor.getWindow().getDecorView(), EditText.class).setText("@bob\ncarol");
        click(editor, "piko_mute_save");
        List<KeywordMuteRule> saved = AccountMuteStore.load(preferences);
        assertEquals(3, saved.size());
        assertEquals(KeywordMuteRule.Mode.WEEKLY, saved.get(0).mode);
        assertEquals("original", saved.get(0).groupId);
        assertEquals("bob", saved.get(1).keyword);
        assertEquals("carol", saved.get(2).keyword);
        assertEquals(saved.get(1).groupId, saved.get(2).groupId);
    }

    @Test public void malformedStorageIsPreservedAndCannotBeOverwrittenByAdding() throws Exception {
        preferences.edit().putString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "broken-json").commit();
        open(new KeywordMuteFragment());
        View fab = field(manager(), "addButton");
        assertFalse(fab.isEnabled());
        assertEquals(0, find(fragment.getView(), ListView.class).getAdapter().getCount());
        assertEquals("broken-json", preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, ""));
    }

    @Test public void leavingScreenDismissesEditor() throws Exception {
        open(new KeywordMuteFragment());
        View fab = field(manager(), "addButton");
        fab.performClick();
        Dialog editor = dialogs().get(0);
        activity.getFragmentManager().beginTransaction().remove(fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        assertFalse(editor.isShowing());
        assertTrue(dialogs().isEmpty());
    }

    @Test public void selectionUsesTheSameListWithLeftCheckboxesAndDoesNotChangeMuting() throws Exception {
        KeywordMuteStore.save(preferences, List.of(weekly("a", "first phrase", "a"),
                weekly("b", "second phrase", "b")));
        String before = preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "");
        open(new KeywordMuteFragment());
        ListView list = find(fragment.getView(), ListView.class);
        TextView normalTitle = find(ruleRow(0), TextView.class);
        float textSize = normalTitle.getTextSize();
        assertTrue(ruleRow(0).performLongClick());
        assertTrue(dialogs().isEmpty());
        assertSame(list, find(fragment.getView(), ListView.class));
        ViewGroup row = (ViewGroup) ruleRow(0);
        assertTrue(row.getChildAt(0) instanceof CheckBox);
        assertTrue(((CheckBox) row.getChildAt(0)).isChecked());
        assertEquals("first phrase", find(row.getChildAt(1), TextView.class).getText().toString());
        assertEquals(textSize, find(row.getChildAt(1), TextView.class).getTextSize(), 0f);
        assertFalse(find(row, SettingsUi.SwitchControl.class).isEnabled());
        ruleRow(1).performClick();
        assertTrue(find(ruleRow(1), CheckBox.class).isChecked());
        assertEquals(before, preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, ""));
        View root = fragment.getView();
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 1000);
        // The list uses the page's available space instead of a fixed-height dialog list.
        assertTrue(list.getHeight() > 240 * activity.getResources().getDisplayMetrics().density);
    }

    @Test public void clearingAndCancellingSelectionRestoresNormalRowsWithoutWriting() throws Exception {
        KeywordMuteStore.save(preferences, List.of(weekly("a", "phrase", "a")));
        String before = preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, "");
        open(new KeywordMuteFragment());
        ruleRow(0).performLongClick();
        find(ruleRow(0), CheckBox.class).setChecked(false);
        ButtonView edit = field(manager(), "editSelection");
        ButtonView delete = field(manager(), "deleteSelection");
        assertFalse(edit.isEnabled());
        assertFalse(delete.isEnabled());
        ButtonView cancel = button(fragment.getView(), activity.getString(android.R.string.cancel));
        assertNotNull(cancel);
        cancel.performClick();
        assertNull(find(ruleRow(0), CheckBox.class));
        assertTrue(find(ruleRow(0), SettingsUi.SwitchControl.class).isEnabled());
        View fab = field(manager(), "addButton");
        assertEquals(View.VISIBLE, fab.getVisibility());
        assertEquals(before, preferences.getString(MuteSettings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key, ""));
    }

    @Test public void selectionDeleteRetainsUnselectedRulesAndLaterAdditions() throws Exception {
        KeywordMuteRule first = weekly("a", "first", "a");
        KeywordMuteRule second = weekly("b", "second", "b");
        KeywordMuteStore.save(preferences, List.of(first, second));
        open(new KeywordMuteFragment());
        ruleRow(0).performLongClick();
        KeywordMuteStore.save(preferences, List.of(first, second, weekly("c", "later", "c")));
        ButtonView delete = field(manager(), "deleteSelection");
        delete.performClick();
        List<KeywordMuteRule> saved = KeywordMuteStore.load(preferences);
        assertEquals(2, saved.size());
        assertEquals("b", saved.get(0).id);
        assertEquals("c", saved.get(1).id);
        assertNull(find(ruleRow(0), CheckBox.class));
    }

    @Test public void selectionScheduleEditorOnlyChangesSelectedRules() throws Exception {
        KeywordMuteStore.save(preferences, List.of(weekly("a", "first", "a"),
                disabled(weekly("b", "second", "b")), weekly("c", "third", "c")));
        open(new KeywordMuteFragment());
        ruleRow(0).performLongClick();
        ruleRow(1).performClick();
        ButtonView edit = field(manager(), "editSelection");
        edit.performClick();
        Dialog editor = dialogs().get(0);
        click(editor, "piko_mute_save");
        List<KeywordMuteRule> saved = KeywordMuteStore.load(preferences);
        assertEquals(KeywordMuteRule.Mode.ALWAYS, saved.get(0).mode);
        assertEquals(KeywordMuteRule.Mode.ALWAYS, saved.get(1).mode);
        assertEquals(KeywordMuteRule.Mode.WEEKLY, saved.get(2).mode);
        assertEquals("first", saved.get(0).keyword);
        assertEquals("second", saved.get(1).keyword);
        assertTrue(saved.get(0).enabled);
        assertFalse(saved.get(1).enabled);
    }

    @Test public void accountPeriodEditingPreservesDifferentEnabledStates() throws Exception {
        AccountMuteStore.save(preferences, List.of(weekly("a", "alice", "a"),
                disabled(weekly("b", "bob", "b"))));
        open(new AccountMuteFragment());
        ruleRow(0).performLongClick();
        ruleRow(1).performClick();
        ButtonView edit = field(manager(), "editSelection");
        edit.performClick();
        Dialog editor = dialogs().get(0);
        assertNull(find(editor.getWindow().getDecorView(), SettingsUi.SwitchControl.class));
        click(editor, "piko_mute_save");
        List<KeywordMuteRule> saved = AccountMuteStore.load(preferences);
        assertEquals(2, saved.size());
        assertEquals(KeywordMuteRule.Mode.ALWAYS, saved.get(0).mode);
        assertEquals(KeywordMuteRule.Mode.ALWAYS, saved.get(1).mode);
        assertTrue(saved.get(0).enabled);
        assertFalse(saved.get(1).enabled);
        assertEquals("b", saved.get(1).groupId);
    }

    private static KeywordMuteRule disabled(KeywordMuteRule rule) {
        return new KeywordMuteRule(rule.id, rule.keyword, false, rule.mode, rule.until,
                rule.startDay, rule.startMinute, rule.endDay, rule.endMinute, rule.groupId);
    }

    private void open(MuteRulesFragment screen) {
        fragment = screen;
        activity.getFragmentManager().beginTransaction().add(android.R.id.content, screen).commit();
        activity.getFragmentManager().executePendingTransactions();
    }

    private View ruleRow(int index) {
        ListView list = find(fragment.getView(), ListView.class);
        return list.getAdapter().getView(index, null, list);
    }

    private Object manager() throws Exception { return field(fragment, "manager"); }
    private List<Dialog> dialogs() throws Exception { return field(fragment, "dialogs"); }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        Class<?> type = object instanceof MuteRulesFragment ? MuteRulesFragment.class : object.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> settings() throws Exception {
        Field field = SettingsRegistry.class.getDeclaredField("SETTINGS");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static KeywordMuteRule weekly(String id, String phrase, String group) {
        return new KeywordMuteRule(id, phrase, true, KeywordMuteRule.Mode.WEEKLY, 0, 1, 600, 5, 900, group);
    }

    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            T result = find(group.getChildAt(i), type);
            if (result != null) return result;
        }
        return null;
    }

    private static void click(Dialog dialog, String text) {
        ButtonView button = button(dialog.getWindow().getDecorView(), text);
        assertNotNull(button);
        button.performClick();
    }

    private static ButtonView button(View root, String text) {
        if (root instanceof ButtonView button && text.contentEquals(button.getText())) return button;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ButtonView button = button(group.getChildAt(i), text);
            if (button != null) return button;
        }
        return null;
    }
}
