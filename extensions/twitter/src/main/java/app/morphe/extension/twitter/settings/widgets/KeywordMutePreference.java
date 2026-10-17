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

/** Rule list and editor, using platform date/time pickers at minute precision. */
@SuppressWarnings("deprecation")
public final class KeywordMutePreference extends Preference {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm XXX");

    public KeywordMutePreference(Context context) {
        super(context);
        setKey(Settings.TIMELINE_STRICT_KEYWORD_MUTE_RULES.key);
        setPersistent(false);
        setTitle(str("piko_pref_strict_keyword_mute_keywords"));
        setSummary(str("piko_mute_manage_summary"));
        setSingleLineTitle(false);
        setOnPreferenceClickListener(ignored -> { new Manager().show(); return true; });
    }

    private final class Manager {
        private final PikoSharedPrefCategory preferences = new PikoSharedPrefCategory(Settings.SHARED_PREF_NAME);
        private final List<KeywordMuteRule> rules = new ArrayList<>();
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

        private boolean reload() {
            try {
                rules.clear();
                rules.addAll(KeywordMuteStore.load(preferences.preferences));
                adapter.clear();
                for (KeywordMuteRule rule : rules) adapter.add(summary(rule));
                if (rules.isEmpty()) adapter.add(str("piko_mute_empty"));
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
                    .setTitle(str("piko_pref_strict_keyword_mute_keywords"))
                    .setAdapter(adapter, (dialog, index) -> { })
                    .setPositiveButton(str("piko_mute_add"), null)
                    .setNegativeButton(android.R.string.cancel, null).create();
            listDialog.setOnDismissListener(ignored -> handler.removeCallbacks(tick));
            listDialog.show();
            listDialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) { handler.removeCallbacks(tick); }
            });
            // Override the default list listener so the list remains behind the editor.
            listDialog.getListView().setOnItemClickListener((parent, view, index, id) -> {
                if (index < rules.size()) edit(rules.get(index));
            });
            listDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> edit(null));
            tick.run();
        }

        private String summary(KeywordMuteRule rule) {
            long now = System.currentTimeMillis();
            boolean globallyEnabled = preferences.preferences.getBoolean(Settings.TIMELINE_STRICT_KEYWORD_MUTE.key,
                    Settings.TIMELINE_STRICT_KEYWORD_MUTE.defaultValue);
            String status = !rule.enabled || !globallyEnabled ? str("piko_mute_disabled")
                    : rule.activeUntil(now, ZoneId.systemDefault()) > now ? str("piko_mute_active")
                    : rule.mode == KeywordMuteRule.Mode.UNTIL ? str("piko_mute_expired")
                    : str("piko_mute_outside");
            String period = rule.mode == KeywordMuteRule.Mode.ALWAYS ? str("piko_mute_always")
                    : rule.mode == KeywordMuteRule.Mode.UNTIL
                    ? str("piko_mute_until") + " " + DATE_TIME.format(Instant.ofEpochMilli(rule.until).atZone(ZoneId.systemDefault()))
                    : str("piko_mute_weekly") + " " + day(rule.startDay) + " " + time(rule.startMinute)
                    + " → " + day(rule.endDay) + " " + time(rule.endMinute);
            return rule.keyword + "\n" + status + " · " + period;
        }

        private void edit(KeywordMuteRule original) {
            Context context = getContext();
            ZoneId zone = ZoneId.systemDefault();
            LinearLayout form = new LinearLayout(context);
            form.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
            form.setPadding(padding, padding / 2, padding, padding / 2);
            TextView help = label(form, str("piko_mute_schedule_help") + "\n" + zone.getId());
            help.setTextIsSelectable(true);
            EditText keyword = new EditText(context);
            keyword.setHint(str("piko_mute_keyword"));
            keyword.setInputType(InputType.TYPE_CLASS_TEXT);
            keyword.setSingleLine(true);
            keyword.setText(original == null ? "" : original.keyword);
            form.addView(keyword);
            Switch enabled = new Switch(context);
            enabled.setText(str("piko_mute_enabled"));
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
                    .setTitle(str(original == null ? "piko_mute_add" : "piko_mute_edit"))
                    .setView(scroll).setPositiveButton(str("piko_mute_save"), null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(original == null ? null : str("piko_mute_delete"), null).create();
            editor.show();
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String entered = keyword.getText().toString();
                String normalized = KeywordMuteRule.normalize(entered);
                if (normalized.isEmpty() || entered.contains("\n") || entered.contains("\r")) {
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
                try {
                    // Retain other rules that changed outside this editor.
                    List<KeywordMuteRule> updated = new ArrayList<>(KeywordMuteStore.load(preferences.preferences));
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
                    KeywordMuteStore.save(preferences.preferences, updated);
                    editor.dismiss();
                    reload();
                } catch (Exception e) { error.setText(str("piko_mute_save_error")); }
            });
            if (original != null) editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                try {
                    List<KeywordMuteRule> updated = new ArrayList<>(KeywordMuteStore.load(preferences.preferences));
                    updated.removeIf(rule -> rule.id.equals(original.id));
                    KeywordMuteStore.save(preferences.preferences, updated);
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
