/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.widgets;

import static app.morphe.extension.shared.StringRef.str;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteRule;
import app.morphe.extension.twitter.patches.keywordmute.KeywordMuteStore;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.patches.accountmute.AccountMuteStore;
import app.morphe.extension.twitter.patches.accountmute.AccountTargets;

/** Rule list and editor, using platform date/time pickers at minute precision. */
@SuppressWarnings("deprecation")
public final class KeywordMutePreference extends Preference {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm XXX");

    private final boolean accounts;
    private final String titleKey;

    public KeywordMutePreference(Context context) { this(context, false); }

    public KeywordMutePreference(Context context, boolean accounts) {
        super(context);
        this.accounts = accounts;
        this.titleKey = accounts ? "piko_account_mute_manage" : "piko_pref_strict_keyword_mute_keywords";
        setKey(accounts ? Settings.ACCOUNT_MUTE_RULES.key : Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key);
        setPersistent(false);
        setTitle(str(titleKey));
        setSummary(str(accounts ? "piko_account_mute_summary" : "piko_mute_manage_summary"));
        setSingleLineTitle(false);
        setOnPreferenceClickListener(ignored -> { new Manager().show(); return true; });
    }

    private final class Manager {
        private final PikoSharedPrefCategory preferences = new PikoSharedPrefCategory(Settings.SHARED_PREF_NAME);
        private final List<KeywordMuteRule> rules = new ArrayList<>();
        private final List<List<KeywordMuteRule>> rows = new ArrayList<>();
        private final ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private AlertDialog listDialog;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (listDialog == null || !listDialog.isShowing()
                        || !listDialog.getWindow().getDecorView().isAttachedToWindow()) return;
                if (!reload()) return;
                handler.postDelayed(this, 60000 - Math.floorMod(System.currentTimeMillis(), 60000));
            }
        };

        private List<KeywordMuteRule> loadRules() throws org.json.JSONException {
            return accounts ? AccountMuteStore.load(preferences.preferences) : KeywordMuteStore.load(preferences.preferences);
        }
        private void saveRules(List<KeywordMuteRule> rules) throws org.json.JSONException {
            if (accounts) AccountMuteStore.save(preferences.preferences, rules);
            else KeywordMuteStore.save(preferences.preferences, rules);
        }

        private boolean reload() {
            try {
                rules.clear();
                rules.addAll(loadRules());
                rows.clear();
                if (accounts) rows.addAll(AccountMuteStore.groups(rules));
                else for (KeywordMuteRule rule : rules) rows.add(java.util.Collections.singletonList(rule));
                adapter.clear();
                for (List<KeywordMuteRule> row : rows) adapter.add(summary(row));
                if (rules.isEmpty()) adapter.add(str(accounts ? "piko_account_mute_empty" : "piko_mute_empty"));
                return true;
            } catch (Exception e) {
                handler.removeCallbacks(tick);
                if (listDialog != null) listDialog.dismiss();
                new AlertDialog.Builder(getContext()).setMessage(str("piko_mute_data_error"))
                        .setPositiveButton(android.R.string.ok, null).show();
                return false;
            }
        }

        void show() {
            if (!reload()) return;
            listDialog = new AlertDialog.Builder(getContext())
                    .setTitle(str(titleKey))
                    .setAdapter(adapter, (dialog, index) -> { })
                    .setPositiveButton(str(accounts ? "piko_account_mute_add" : "piko_mute_add"), null)
                    .setNegativeButton(android.R.string.cancel, null).create();
            listDialog.setOnDismissListener(ignored -> handler.removeCallbacks(tick));
            listDialog.show();
            listDialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) { handler.removeCallbacks(tick); }
            });
            // Override the default list listener so the list remains behind the editor.
            listDialog.getListView().setOnItemClickListener((parent, view, index, id) -> {
                if (index < rows.size()) {
                    List<KeywordMuteRule> row = rows.get(index);
                    edit(row.get(0), accounts ? row : null);
                }
            });
            listDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> edit(null));
            listDialog.getListView().setOnItemLongClickListener((parent, view, index, id) -> {
                if (index >= rows.size()) return false;
                selectRules(rows.get(index).get(0).id);
                return true;
            });
            tick.run();
        }

        private String summary(KeywordMuteRule rule) {
            long now = System.currentTimeMillis();
            boolean globallyEnabled = preferences.preferences.getBoolean(accounts ? Settings.ACCOUNT_MUTE.key : Settings.TIMELINE_STRICT_KEYWORD_MUTE.key, true);
            String status = !rule.enabled || !globallyEnabled ? str("piko_mute_disabled")
                    : rule.activeUntil(now, ZoneId.systemDefault()) > now ? str("piko_mute_active")
                    : rule.mode == KeywordMuteRule.Mode.UNTIL ? str("piko_mute_expired")
                    : str("piko_mute_outside");
            String period = rule.mode == KeywordMuteRule.Mode.ALWAYS ? str("piko_mute_always")
                    : rule.mode == KeywordMuteRule.Mode.UNTIL
                    ? str("piko_mute_until") + " " + DATE_TIME.format(Instant.ofEpochMilli(rule.until).atZone(ZoneId.systemDefault()))
                    : str("piko_mute_weekly") + " " + day(rule.startDay) + " " + time(rule.startMinute)
                    + " → " + day(rule.endDay) + " " + time(rule.endMinute);
            return (accounts ? "@" : "") + rule.keyword + "\n" + status + " · " + period;
        }

        private void edit(KeywordMuteRule original) { edit(original, null); }

        private String summary(List<KeywordMuteRule> group) {
            if (!accounts) return summary(group.get(0));
            List<String> handles = new ArrayList<>();
            for (KeywordMuteRule rule : group) handles.add("@" + rule.keyword);
            String first = summary(group.get(0));
            return String.join("\n", handles) + first.substring(first.indexOf('\n'));
        }

        private void selectRules(String initialId) {
            if (!reload() || rules.isEmpty()) return;
            List<List<KeywordMuteRule>> snapshot = new ArrayList<>(rows);
            String[] labels = new String[snapshot.size()];
            boolean[] selected = new boolean[labels.length];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = summary(snapshot.get(i));
                selected[i] = snapshot.get(i).stream().anyMatch(rule -> rule.id.equals(initialId));
            }
            LinearLayout footer = new LinearLayout(getContext());
            footer.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * getContext().getResources().getDisplayMetrics().density);
            footer.setPadding(padding, 0, padding, 0);
            LinearLayout actions = section(footer);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button all = button(actions, str("piko_mute_all"));
            Button delete = button(actions, str("piko_mute_delete"));
            all.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            delete.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView error = label(footer, "");
            error.setVisibility(View.GONE);
            AlertDialog picker = new AlertDialog.Builder(getContext())
                    .setTitle(str("piko_mute_selected", "0"))
                    .setMultiChoiceItems(labels, selected, (dialog, index, checked) -> {
                        selected[index] = checked;
                        updateSelection((AlertDialog) dialog, selected, delete);
                    })
                    .setView(footer)
                    .setPositiveButton(str("piko_mute_edit_schedules"), null)
                    .setNegativeButton(android.R.string.cancel, null).create();
            picker.show();
            updateSelection(picker, selected, delete);
            all.setOnClickListener(view -> {
                for (int i = 0; i < selected.length; i++) { selected[i] = true; picker.getListView().setItemChecked(i, true); }
                updateSelection(picker, selected, delete);
            });
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<KeywordMuteRule> schedules = new ArrayList<>();
                int count = 0;
                for (int i = 0; i < selected.length; i++) if (selected[i]) {
                    schedules.addAll(snapshot.get(i));
                    count++;
                }
                if (schedules.isEmpty()) return;
                picker.dismiss(); edit(count == 1 ? schedules.get(0) : null, schedules);
            });
            delete.setOnClickListener(view -> {
                java.util.Set<String> selectedIds = new java.util.HashSet<>();
                for (int i = 0; i < selected.length; i++) if (selected[i]) {
                    for (KeywordMuteRule rule : snapshot.get(i)) selectedIds.add(rule.id);
                }
                if (selectedIds.isEmpty()) return;
                try {
                    // Read current storage so unselected schedules and later additions survive.
                    List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                    updated.removeIf(rule -> selectedIds.contains(rule.id));
                    saveRules(updated);
                    picker.dismiss();
                    reload();
                } catch (Exception e) {
                    error.setText(str("piko_mute_save_error"));
                    error.setVisibility(View.VISIBLE);
                }
            });
        }

        private void updateSelection(AlertDialog picker, boolean[] selected, Button delete) {
            int count = 0;
            for (boolean checked : selected) if (checked) count++;
            picker.setTitle(str("piko_mute_selected", String.valueOf(count)));
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(count > 0);
            delete.setEnabled(count > 0);
        }

        private void edit(KeywordMuteRule original, List<KeywordMuteRule> batch) {
            Context context = getContext();
            boolean bulkPeriod = batch != null && original == null;
            ZoneId zone = ZoneId.systemDefault();
            LinearLayout form = new LinearLayout(context);
            form.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
            form.setPadding(padding, padding / 2, padding, padding / 2);
            TextView help = label(form, str("piko_mute_schedule_help") + "\n" + zone.getId());
            help.setTextIsSelectable(true);
            EditText keyword = new EditText(context);
            keyword.setHint(str(accounts ? "piko_account_mute_input" : "piko_mute_keyword"));
            keyword.setInputType(InputType.TYPE_CLASS_TEXT | (accounts || bulkPeriod ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
            keyword.setSingleLine(!accounts && !bulkPeriod);
            if (accounts || bulkPeriod) { keyword.setMinLines(3); keyword.setMaxLines(8); }
            java.util.Set<String> editedIds = new java.util.HashSet<>();
            java.util.Set<String> batchHandles = new java.util.LinkedHashSet<>();
            if (original != null) editedIds.add(original.id);
            if (batch != null) for (KeywordMuteRule rule : batch) { editedIds.add(rule.id); batchHandles.add(rule.keyword); }
            keyword.setText(batch != null ? String.join("\n", batchHandles) : original == null ? "" : original.keyword);
            if (bulkPeriod) keyword.setEnabled(false);
            form.addView(keyword);
            Switch enabled = new Switch(context);
            enabled.setText(str(accounts ? "piko_account_mute_enabled" : "piko_mute_enabled"));
            enabled.setChecked(original == null || original.enabled);
            form.addView(enabled);
            label(form, str("piko_mute_period"));
            Spinner mode = spinner(form, new String[]{str("piko_mute_always"), str("piko_mute_until"), str("piko_mute_weekly")});
            mode.setSelection(original == null ? 0 : original.mode.ordinal());

            LinearLayout expiryForm = section(form);
            long defaultUntil = ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0).toInstant().toEpochMilli();
            long[] until = {original != null && original.mode == KeywordMuteRule.Mode.UNTIL ? original.until : defaultUntil};
            Button date = button(expiryForm, "");
            Button expiryTime = button(expiryForm, "");
            Runnable showExpiry = () -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                date.setText(str("piko_mute_date") + " " + value.toLocalDate());
                expiryTime.setText(str("piko_mute_time") + " " + time(value.getHour() * 60 + value.getMinute()));
            };
            showExpiry.run();
            date.setOnClickListener(view -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                new DatePickerDialog(context, (picker, year, month, day) -> {
                    until[0] = java.time.LocalDate.of(year, month + 1, day).atTime(value.toLocalTime())
                            .atZone(zone).toInstant().toEpochMilli();
                    showExpiry.run();
                }, value.getYear(), value.getMonthValue() - 1, value.getDayOfMonth()).show();
            });
            expiryTime.setOnClickListener(view -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                new TimePickerDialog(context, (picker, hour, minute) -> {
                    until[0] = value.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
                    showExpiry.run();
                }, value.getHour(), value.getMinute(), DateFormat.is24HourFormat(context)).show();
            });

            LinearLayout weeklyForm = section(form);
            label(weeklyForm, str("piko_mute_start"));
            String[] days = new String[7];
            for (int i = 0; i < 7; i++) days[i] = day(i + 1);
            Spinner startDay = spinner(weeklyForm, days);
            startDay.setSelection(original == null ? 0 : original.startDay - 1);
            int[] startMinute = {original == null ? 0 : original.startMinute};
            timeButton(weeklyForm, startMinute);
            label(weeklyForm, str("piko_mute_end"));
            Spinner endDay = spinner(weeklyForm, days);
            endDay.setSelection(original == null ? 0 : original.endDay - 1);
            int[] endMinute = {original == null ? 60 : original.endMinute};
            timeButton(weeklyForm, endMinute);
            mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    expiryForm.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                    weeklyForm.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            expiryForm.setVisibility(mode.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);
            weeklyForm.setVisibility(mode.getSelectedItemPosition() == 2 ? View.VISIBLE : View.GONE);
            TextView error = label(form, "");
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.addView(form);
            AlertDialog editor = new AlertDialog.Builder(context)
                    .setTitle(str(accounts ? "piko_account_mute_manage" : bulkPeriod ? "piko_mute_edit_schedules"
                            : original == null ? "piko_mute_add" : "piko_mute_edit"))
                    .setView(scroll).setPositiveButton(str("piko_mute_save"), null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(original == null ? null : str("piko_mute_delete"), null).create();
            editor.show();
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String entered = keyword.getText().toString();
                String normalized = KeywordMuteRule.normalize(entered);
                if (!accounts && !bulkPeriod && (normalized.isEmpty() || entered.contains("\n") || entered.contains("\r"))) {
                    error.setText(str("piko_mute_keyword_error")); return;
                }
                KeywordMuteRule.Mode selected = KeywordMuteRule.Mode.values()[mode.getSelectedItemPosition()];
                if (selected == KeywordMuteRule.Mode.UNTIL && until[0] <= System.currentTimeMillis()
                        && (original == null || original.mode != selected || original.until != until[0])) {
                    error.setText(str("piko_mute_past_error")); return;
                }
                if (selected == KeywordMuteRule.Mode.WEEKLY && startDay.getSelectedItemPosition() == endDay.getSelectedItemPosition()
                        && startMinute[0] == endMinute[0]) {
                    error.setText(str("piko_mute_week_error")); return;
                }
                if (accounts) {
                    AccountTargets.Batch parsed = AccountTargets.parse(entered);
                    if (parsed.handles.isEmpty() || !parsed.invalid.isEmpty()) {
                        error.setText(str("piko_account_mute_invalid") + "\n" + String.join("\n", parsed.invalid)); return;
                    }
                    KeywordMuteRule period = new KeywordMuteRule("period", parsed.handles.get(0), enabled.isChecked(),
                            selected, until[0], startDay.getSelectedItemPosition() + 1, startMinute[0],
                            endDay.getSelectedItemPosition() + 1, endMinute[0]);
                    try {
                        saveRules(AccountMuteStore.withSchedules(loadRules(), parsed.handles, period, editedIds));
                        editor.dismiss(); reload();
                    } catch (IllegalArgumentException e) { error.setText(str("piko_mute_data_changed")); }
                    catch (Exception e) { error.setText(str("piko_mute_save_error")); }
                    return;
                }
                try {
                    // Retain other rules that changed outside this editor.
                    List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                    if (bulkPeriod) {
                        KeywordMuteRule period = new KeywordMuteRule("period", "period", enabled.isChecked(), selected,
                                until[0], startDay.getSelectedItemPosition() + 1, startMinute[0],
                                endDay.getSelectedItemPosition() + 1, endMinute[0]);
                        saveRules(KeywordMuteStore.withSchedules(updated, period, editedIds));
                        editor.dismiss(); reload(); return;
                    }
                    int index = -1;
                    for (int i = 0; i < updated.size(); i++) {
                        KeywordMuteRule other = updated.get(i);
                        if (original != null && other.id.equals(original.id)) index = i;
                        else if (other.normalizedKeyword.equals(normalized)) {
                            error.setText(str("piko_mute_duplicate_error")); return;
                        }
                    }
                    if (original != null && index < 0) { error.setText(str("piko_mute_data_changed")); return; }
                    KeywordMuteRule rule = new KeywordMuteRule(original == null ? UUID.randomUUID().toString() : original.id,
                            keyword.getText().toString(), enabled.isChecked(), selected, until[0],
                            startDay.getSelectedItemPosition() + 1, startMinute[0], endDay.getSelectedItemPosition() + 1, endMinute[0]);
                    if (index < 0) updated.add(rule); else updated.set(index, rule);
                    saveRules(updated);
                    editor.dismiss();
                    reload();
                } catch (IllegalArgumentException e) { error.setText(str("piko_mute_data_changed")); }
                catch (Exception e) { error.setText(str("piko_mute_save_error")); }
            });
            if (original != null) editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                try {
                    List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                    updated.removeIf(rule -> editedIds.contains(rule.id));
                    saveRules(updated);
                    editor.dismiss();
                    reload();
                } catch (Exception e) { error.setText(str("piko_mute_save_error")); }
            });
        }

        private void timeButton(LinearLayout form, int[] value) {
            Button button = button(form, time(value[0]));
            button.setOnClickListener(view -> new TimePickerDialog(getContext(), (picker, hour, minute) -> {
                value[0] = hour * 60 + minute;
                button.setText(time(value[0]));
            }, value[0] / 60, value[0] % 60, DateFormat.is24HourFormat(getContext())).show());
        }
    }

    private static String day(int day) { return DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()); }
    private static String time(int minute) { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }
    private TextView label(LinearLayout form, String text) {
        TextView label = new TextView(getContext()); label.setText(text); form.addView(label); return label;
    }
    private LinearLayout section(LinearLayout form) {
        LinearLayout section = new LinearLayout(getContext()); section.setOrientation(LinearLayout.VERTICAL);
        form.addView(section); return section;
    }
    private Button button(LinearLayout form, String text) {
        Button button = new Button(getContext()); button.setText(text); form.addView(button); return button;
    }
    private Spinner spinner(LinearLayout form, String[] items) {
        Spinner spinner = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); form.addView(spinner); return spinner;
    }
}
