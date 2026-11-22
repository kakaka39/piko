/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.mute;

import static app.morphe.extension.shared.StringRef.str;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
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

import app.morphe.extension.crimera.settings.CustomScreenFragment;
import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.DialogView;
import app.morphe.extension.newx.mute.accountmute.AccountMuteStore;
import app.morphe.extension.newx.mute.accountmute.AccountTargets;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteRule;
import app.morphe.extension.newx.mute.keywordmute.KeywordMuteStore;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;

/** Independent scheduled mute screens using the same components as Piko post filtering. */
@SuppressWarnings("deprecation")
public abstract class MuteRulesFragment extends CustomScreenFragment {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private boolean accounts;
    private Manager manager;
    private final List<Dialog> dialogs = new ArrayList<>();

    protected abstract boolean accountRules();

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        accounts = accountRules();
        manager = new Manager();
        return manager.createView();
    }

    @Override public void onResume() {
        super.onResume();
        if (getActivity() instanceof NewXSettingsActivity activity) {
            activity.setPageTitle(str(accounts ? "piko_newx_custom_account_rules_title"
                    : "piko_newx_custom_keyword_rules_title"));
        }
        if (manager != null) manager.start();
    }

    @Override public void onPause() {
        if (manager != null) manager.stop();
        super.onPause();
    }

    @Override public void onDestroyView() {
        if (manager != null) manager.stop();
        for (Dialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
        manager = null;
        super.onDestroyView();
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Mute settings activity is missing");
        return context;
    }

    private void show(DialogView dialog) {
        track(dialog.getDialog());
        dialog.getDialog().setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static void setInteractionsEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                setInteractionsEnabled(group.getChildAt(i), enabled);
        }
    }

    private void track(Dialog dialog) {
        dialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> dialogs.remove(dialog));
    }

    private final class Manager {
        private final PikoSharedPrefCategory preferences = new PikoSharedPrefCategory(MuteSettings.SHARED_PREF_NAME);
        private final List<KeywordMuteRule> rules = new ArrayList<>();
        private final List<List<KeywordMuteRule>> rows = new ArrayList<>();
        private final RuleAdapter adapter = new RuleAdapter();
        private FrameLayout root, rulesContainer;
        private TextView emptyState;
        private View addButton;
        private SettingsUi.SwitchRow masterSwitch, conversations;
        private boolean loaded, selecting;
        private final java.util.Set<String> selectedRows = new java.util.HashSet<>();
        private LinearLayout settingsHeader, selectionHeader, selectionActions;
        private ListView ruleList;
        private TextView selectionCount, selectionError;
        private ButtonView editSelection, deleteSelection;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (root == null || !root.isAttachedToWindow()) return;
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
                List<KeywordMuteRule> current = loadRules();
                rules.clear();
                rules.addAll(current);
                rows.clear();
                if (accounts) rows.addAll(AccountMuteStore.groups(rules));
                else for (KeywordMuteRule rule : rules) rows.add(java.util.Collections.singletonList(rule));
                loaded = true;
                emptyState.setText(str(accounts ? "piko_account_mute_empty" : "piko_mute_empty"));
                emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            } catch (Exception e) {
                loaded = false;
                rules.clear();
                rows.clear();
                emptyState.setText(str("piko_mute_data_error"));
                emptyState.setVisibility(View.VISIBLE);
                stop();
            }
            masterSwitch.setOnCheckedChangeListener(null);
            masterSwitch.setChecked(MuteSettings.enabled(accounts), false);
            masterSwitch.setOnCheckedChangeListener(enabled -> {
                if (SettingsRegistry.settingOrNull(accounts ? MuteSettings.ACCOUNTS_ENABLED
                        : MuteSettings.KEYWORDS_ENABLED) instanceof BooleanSetting setting) setting.save(enabled);
                refreshEnabled();
            });
            if (conversations != null) {
                conversations.setOnCheckedChangeListener(null);
                conversations.setChecked(MuteSettings.conversations(), false);
                conversations.setOnCheckedChangeListener(enabled -> {
                    if (SettingsRegistry.settingOrNull(MuteSettings.CONVERSATIONS) instanceof BooleanSetting setting)
                        setting.save(enabled);
                });
            }
            java.util.Set<String> currentIds = new java.util.HashSet<>();
            for (List<KeywordMuteRule> group : rows) currentIds.add(group.get(0).id);
            selectedRows.retainAll(currentIds);
            refreshEnabled();
            return loaded;
        }

        View createView() {
            Context context = requireContext();
            root = new FrameLayout(context);
            root.setBackgroundColor(SettingsUi.backgroundColor(context));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            root.addView(content, new FrameLayout.LayoutParams(-1, -1));
            settingsHeader = new LinearLayout(context);
            settingsHeader.setOrientation(LinearLayout.VERTICAL);
            content.addView(settingsHeader, new LinearLayout.LayoutParams(-1, -2));
            masterSwitch = SettingsUi.switchRow(context, str(accounts
                    ? "piko_newx_custom_account_mute_enabled" : "piko_newx_custom_keyword_mute_enabled"), null,
                    MuteSettings.enabled(accounts));
            settingsHeader.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));
            if (!accounts && SettingsRegistry.isRegistered(MuteSettings.CONVERSATIONS)) {
                conversations = SettingsUi.switchRow(context,
                        str("piko_pref_strict_keyword_mute_conversations"), null, MuteSettings.conversations());
                settingsHeader.addView(conversations, new LinearLayout.LayoutParams(-1, -2));
            }
            settingsHeader.addView(SettingsUi.divider(context));
            createSelectionHeader(content);
            rulesContainer = new FrameLayout(context);
            content.addView(rulesContainer, new LinearLayout.LayoutParams(-1, 0, 1f));
            ListView list = new ListView(context);
            list.setDivider(null);
            list.setDividerHeight(0);
            list.setClipToPadding(false);
            list.setPadding(0, Theme.dpToPx(context, 4f), 0, Theme.dpToPx(context, 96f));
            list.setAdapter(adapter);
            ruleList = list;
            rulesContainer.addView(list, new FrameLayout.LayoutParams(-1, -1));
            emptyState = SettingsUi.summaryText(context);
            emptyState.setGravity(Gravity.CENTER);
            int padding = Theme.dpToPx(context, 32f);
            emptyState.setPadding(padding, padding, padding, padding);
            rulesContainer.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));
            addButton = SettingsUi.floatingActionButton(context,
                    str(accounts ? "piko_account_mute_add" : "piko_mute_add"), ignored -> edit(null));
            FrameLayout.LayoutParams fab = new FrameLayout.LayoutParams(
                    Theme.dpToPx(context, 56f), Theme.dpToPx(context, 56f), Gravity.BOTTOM | Gravity.END);
            fab.setMargins(0, 0, Theme.dpToPx(context, 20f), Theme.dpToPx(context, 20f));
            root.addView(addButton, fab);
            createSelectionActions(content);
            reload();
            return root;
        }

        void start() {
            stop();
            tick.run();
        }

        void stop() { handler.removeCallbacks(tick); }

        private void refreshEnabled() {
            boolean enabled = loaded && MuteSettings.enabled(accounts);
            if (!enabled && selecting) { selecting = false; selectedRows.clear(); }
            if (conversations != null) {
                setInteractionsEnabled(conversations, MuteSettings.enabled(accounts));
                conversations.setAlpha(MuteSettings.enabled(accounts) ? 1f : 0.45f);
            }
            rulesContainer.setAlpha(enabled || !loaded ? 1f : 0.45f);
            addButton.setEnabled(enabled);
            addButton.setAlpha(enabled ? 1f : 0.45f);
            updateSelectionUi();
        }

        private void toggleRules(List<KeywordMuteRule> group, boolean enabled) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (KeywordMuteRule rule : group) ids.add(rule.id);
            try {
                List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                for (int i = 0; i < updated.size(); i++) {
                    KeywordMuteRule rule = updated.get(i);
                    if (ids.remove(rule.id)) updated.set(i, new KeywordMuteRule(rule.id, rule.keyword,
                            enabled, rule.mode, rule.until, rule.startDay, rule.startMinute,
                            rule.endDay, rule.endMinute, rule.groupId));
                }
                if (!ids.isEmpty()) throw new IllegalArgumentException("Mute rule no longer exists");
                saveRules(updated);
            } catch (Exception e) {
                DialogView error = new DialogView(requireContext()).setTitle(str("piko_mute_save_error"));
                ButtonView ok = new ButtonView(requireContext(), ButtonView.ButtonStyle.TEXT,
                        requireContext().getString(android.R.string.ok));
                ok.setOnClickListener(ignored -> error.dismiss());
                error.addButton(ok);
                show(error);
            }
            reload();
        }

        private final class RuleAdapter extends BaseAdapter {
            @Override public int getCount() { return rows.size(); }
            @Override public List<KeywordMuteRule> getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return getItem(position).get(0).id.hashCode(); }
            @Override public View getView(int position, View reusable, ViewGroup parent) {
                Context context = requireContext();
                List<KeywordMuteRule> group = getItem(position);
                String summary = summary(group);
                int separator = summary.lastIndexOf('\n');
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Theme.dpToPx(context, 72f));
                row.setPadding(Theme.dpToPx(context, 20f), Theme.dpToPx(context, 10f),
                        Theme.dpToPx(context, 16f), Theme.dpToPx(context, 10f));
                SettingsUi.applyRippleBackground(row);
                if (selecting) {
                    CheckBox selection = new CheckBox(context);
                    selection.setButtonTintList(new ColorStateList(new int[][] {
                            new int[] {android.R.attr.state_checked}, new int[0]
                    }, new int[] {Theme.checkboxChecked(context), Theme.secondaryText(context)}));
                    selection.setChecked(selectedRows.contains(group.get(0).id));
                    selection.setContentDescription(summary.substring(0, separator));
                    selection.setFocusable(false);
                    selection.setOnCheckedChangeListener((button, checked) -> setSelected(group, checked));
                    LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                            Theme.dpToPx(context, 48f), Theme.dpToPx(context, 48f));
                    checkParams.setMarginEnd(Theme.dpToPx(context, 8f));
                    row.addView(selection, checkParams);
                }
                LinearLayout labels = new LinearLayout(context);
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView title = SettingsUi.titleText(context);
                title.setText(summary.substring(0, separator));
                TextView subtitle = SettingsUi.summaryText(context);
                subtitle.setText(summary.substring(separator + 1));
                subtitle.setPadding(0, Theme.dpToPx(context, 5f), 0, 0);
                labels.addView(title);
                labels.addView(subtitle);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
                labelParams.setMarginEnd(Theme.dpToPx(context, 12f));
                row.addView(labels, labelParams);
                SettingsUi.SwitchControl toggle = new SettingsUi.SwitchControl(context);
                toggle.setInteractive(true);
                toggle.setContentDescription(title.getText());
                toggle.setChecked(group.get(0).enabled, false);
                boolean enabled = loaded && MuteSettings.enabled(accounts);
                toggle.setEnabled(enabled && !selecting);
                toggle.setOnCheckedChangeListener(checked -> { if (enabled && !selecting) toggleRules(group, checked); });
                row.addView(toggle, new LinearLayout.LayoutParams(Theme.dpToPx(context, 52f), Theme.dpToPx(context, 32f)));
                row.setEnabled(enabled);
                row.setOnClickListener(enabled ? ignored -> {
                    if (selecting) setSelected(group, !selectedRows.contains(group.get(0).id));
                    else edit(group.get(0), accounts ? group : null);
                } : null);
                row.setOnLongClickListener(enabled && !selecting
                        ? ignored -> { selectRules(group.get(0).id); return true; } : null);
                return row;
            }
        }

        private String summary(KeywordMuteRule rule) {
            long now = System.currentTimeMillis();
            boolean globallyEnabled = MuteSettings.enabled(accounts);
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

        private void createSelectionHeader(LinearLayout content) {
            Context context = requireContext();
            selectionHeader = new LinearLayout(context);
            selectionHeader.setGravity(Gravity.CENTER_VERTICAL);
            int padding = Theme.dpToPx(context, 20f);
            selectionHeader.setPadding(padding, Theme.dpToPx(context, 4f), padding, 0);
            selectionCount = SettingsUi.titleText(context);
            selectionCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            selectionHeader.addView(selectionCount, new LinearLayout.LayoutParams(0, -2, 1f));
            ButtonView all = button(selectionHeader, str("piko_mute_all"));
            all.setOnClickListener(view -> {
                for (List<KeywordMuteRule> group : rows) selectedRows.add(group.get(0).id);
                updateSelectionUi();
            });
            content.addView(selectionHeader, new LinearLayout.LayoutParams(-1, -2));
        }

        private void createSelectionActions(LinearLayout content) {
            Context context = requireContext();
            selectionActions = new LinearLayout(context);
            selectionActions.setOrientation(LinearLayout.VERTICAL);
            selectionActions.addView(SettingsUi.divider(context));
            selectionError = SettingsUi.summaryText(context);
            selectionError.setTextColor(Color.rgb(244, 33, 46));
            selectionError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            selectionError.setPadding(Theme.dpToPx(context, 20f), 0, Theme.dpToPx(context, 20f), 0);
            selectionError.setVisibility(View.GONE);
            selectionActions.addView(selectionError, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout buttons = new LinearLayout(context);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            selectionActions.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
            ButtonView cancel = button(buttons, context.getString(android.R.string.cancel));
            cancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            cancel.setOnClickListener(view -> finishSelection());
            deleteSelection = button(buttons, str("piko_mute_delete"));
            deleteSelection.setTextColor(Color.rgb(244, 33, 46));
            deleteSelection.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            deleteSelection.setOnClickListener(view -> deleteSelected());
            editSelection = button(selectionActions, str("piko_mute_edit_schedules"));
            editSelection.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            editSelection.setOnClickListener(view -> {
                List<KeywordMuteRule> schedules = new ArrayList<>();
                int count = 0;
                for (List<KeywordMuteRule> group : rows) if (selectedRows.contains(group.get(0).id)) {
                    schedules.addAll(group);
                    count++;
                }
                if (schedules.isEmpty()) return;
                finishSelection();
                edit(count == 1 ? schedules.get(0) : null, schedules);
            });
            content.addView(selectionActions, new LinearLayout.LayoutParams(-1, -2));
        }

        private void selectRules(String initialId) {
            if (!reload() || rules.isEmpty() || !MuteSettings.enabled(accounts)) return;
            selectedRows.clear();
            for (List<KeywordMuteRule> group : rows) {
                if (group.stream().anyMatch(rule -> rule.id.equals(initialId))) selectedRows.add(group.get(0).id);
            }
            selecting = true;
            selectionError.setVisibility(View.GONE);
            updateSelectionUi();
        }

        private void setSelected(List<KeywordMuteRule> group, boolean checked) {
            if (!selecting) return;
            if (checked) selectedRows.add(group.get(0).id);
            else selectedRows.remove(group.get(0).id);
            updateSelectionUi();
        }

        private void finishSelection() {
            selecting = false;
            selectedRows.clear();
            updateSelectionUi();
        }

        private void updateSelectionUi() {
            settingsHeader.setVisibility(selecting ? View.GONE : View.VISIBLE);
            selectionHeader.setVisibility(selecting ? View.VISIBLE : View.GONE);
            selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
            addButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
            ruleList.setPadding(0, Theme.dpToPx(requireContext(), 4f), 0,
                    selecting ? 0 : Theme.dpToPx(requireContext(), 96f));
            selectionCount.setText(str("piko_mute_selected", String.valueOf(selectedRows.size())));
            deleteSelection.setEnabled(!selectedRows.isEmpty());
            editSelection.setEnabled(!selectedRows.isEmpty());
            adapter.notifyDataSetChanged();
        }

        private void deleteSelected() {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (List<KeywordMuteRule> group : rows) if (selectedRows.contains(group.get(0).id)) {
                for (KeywordMuteRule rule : group) ids.add(rule.id);
            }
            if (ids.isEmpty()) return;
            try {
                // Load again so unselected schedules and additions made after selection survive.
                List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                updated.removeIf(rule -> ids.contains(rule.id));
                saveRules(updated);
                finishSelection();
                reload();
            } catch (Exception e) {
                selectionError.setText(str("piko_mute_save_error"));
                selectionError.setVisibility(View.VISIBLE);
            }
        }

        private void edit(KeywordMuteRule original, List<KeywordMuteRule> batch) {
            Context context = requireContext();
            boolean bulkPeriod = batch != null && original == null;
            ZoneId zone = ZoneId.systemDefault();
            LinearLayout form = new LinearLayout(context);
            form.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
            form.setPadding(padding, padding / 2, padding, 0);
            EditText keyword = SettingsUi.textInput(context,
                    str(accounts ? "piko_account_mute_input" : "piko_mute_keyword"),
                    InputType.TYPE_CLASS_TEXT | (accounts || bulkPeriod ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
            keyword.setSingleLine(!accounts && !bulkPeriod);
            if (accounts || bulkPeriod) { keyword.setMinLines(3); keyword.setMaxLines(8); }
            java.util.Set<String> editedIds = new java.util.HashSet<>();
            java.util.Set<String> batchHandles = new java.util.LinkedHashSet<>();
            if (original != null) editedIds.add(original.id);
            if (batch != null) for (KeywordMuteRule rule : batch) { editedIds.add(rule.id); batchHandles.add(rule.keyword); }
            keyword.setText(batch != null ? String.join("\n", batchHandles) : original == null ? "" : original.keyword);
            if (bulkPeriod) keyword.setEnabled(false);
            form.addView(keyword);
            TextView periodLabel = label(form, str("piko_mute_period"));
            LinearLayout.LayoutParams periodLabelParams = new LinearLayout.LayoutParams(-1, -2);
            periodLabelParams.topMargin = Theme.dpToPx(context, 20f);
            periodLabelParams.bottomMargin = Theme.dpToPx(context, 6f);
            periodLabel.setLayoutParams(periodLabelParams);
            Spinner mode = spinner(form, new String[]{str("piko_mute_always"), str("piko_mute_until"), str("piko_mute_weekly")});
            mode.setSelection(original == null ? 0 : original.mode.ordinal());

            LinearLayout expiryForm = section(form);
            expiryForm.setPadding(0, Theme.dpToPx(context, 12f), 0, 0);
            long defaultUntil = ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0).toInstant().toEpochMilli();
            long[] until = {original != null && original.mode == KeywordMuteRule.Mode.UNTIL ? original.until : defaultUntil};
            ButtonView date = button(expiryForm, "");
            ButtonView expiryTime = button(expiryForm, "");
            Runnable showExpiry = () -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                date.setText(str("piko_mute_date") + " " + value.toLocalDate());
                expiryTime.setText(str("piko_mute_time") + " " + time(value.getHour() * 60 + value.getMinute()));
            };
            showExpiry.run();
            date.setOnClickListener(view -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                DatePickerDialog pickerDialog = new DatePickerDialog(context, (picker, year, month, day) -> {
                    until[0] = java.time.LocalDate.of(year, month + 1, day).atTime(value.toLocalTime())
                            .atZone(zone).toInstant().toEpochMilli();
                    showExpiry.run();
                }, value.getYear(), value.getMonthValue() - 1, value.getDayOfMonth());
                track(pickerDialog); pickerDialog.show();
            });
            expiryTime.setOnClickListener(view -> {
                ZonedDateTime value = Instant.ofEpochMilli(until[0]).atZone(zone);
                TimePickerDialog pickerDialog = new TimePickerDialog(context, (picker, hour, minute) -> {
                    until[0] = value.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
                    showExpiry.run();
                }, value.getHour(), value.getMinute(), DateFormat.is24HourFormat(context));
                track(pickerDialog); pickerDialog.show();
            });

            LinearLayout weeklyForm = section(form);
            weeklyForm.setPadding(0, Theme.dpToPx(context, 12f), 0, 0);
            label(weeklyForm, str("piko_mute_start"));
            String[] days = new String[7];
            for (int i = 0; i < 7; i++) days[i] = day(i + 1);
            LinearLayout startRow = scheduleRow(weeklyForm);
            Spinner startDay = spinner(startRow, days);
            startDay.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            startDay.setSelection(original == null ? 0 : original.startDay - 1);
            int[] startMinute = {original == null ? 0 : original.startMinute};
            timeButton(startRow, startMinute);
            TextView endLabel = label(weeklyForm, str("piko_mute_end"));
            LinearLayout.LayoutParams endLabelParams = new LinearLayout.LayoutParams(-1, -2);
            endLabelParams.topMargin = Theme.dpToPx(context, 16f);
            endLabel.setLayoutParams(endLabelParams);
            LinearLayout endRow = scheduleRow(weeklyForm);
            Spinner endDay = spinner(endRow, days);
            endDay.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            endDay.setSelection(original == null ? 0 : original.endDay - 1);
            int[] endMinute = {original == null ? 60 : original.endMinute};
            timeButton(endRow, endMinute);
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
            error.setTextColor(Color.rgb(244, 33, 46));
            DialogView editor = new DialogView(context)
                    .setTitle(str(bulkPeriod ? "piko_mute_edit_schedules" : accounts
                            ? "piko_account_mute_manage" : original == null ? "piko_mute_add" : "piko_mute_edit"))
                    .setScrollableBodyView(form);
            ButtonView cancel = new ButtonView(context, ButtonView.ButtonStyle.TEXT,
                    context.getString(android.R.string.cancel));
            cancel.setOnClickListener(view -> editor.dismiss());
            editor.addButton(cancel);
            ButtonView save = new ButtonView(context, ButtonView.ButtonStyle.TEXT, str("piko_mute_save"));
            save.setOnClickListener(view -> {
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
                    KeywordMuteRule period = new KeywordMuteRule("period", parsed.handles.get(0), original == null || original.enabled,
                            selected, until[0], startDay.getSelectedItemPosition() + 1, startMinute[0],
                            endDay.getSelectedItemPosition() + 1, endMinute[0]);
                    try {
                        List<KeywordMuteRule> current = loadRules();
                        saveRules(preserveEnabledStates(current,
                                AccountMuteStore.withSchedules(current, parsed.handles, period, editedIds)));
                        editor.dismiss(); reload();
                    } catch (IllegalArgumentException e) { error.setText(str("piko_mute_data_changed")); }
                    catch (Exception e) { error.setText(str("piko_mute_save_error")); }
                    return;
                }
                try {
                    // Retain other rules that changed outside this editor.
                    List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                    if (bulkPeriod) {
                        KeywordMuteRule period = new KeywordMuteRule("period", "period", true, selected,
                                until[0], startDay.getSelectedItemPosition() + 1, startMinute[0],
                                endDay.getSelectedItemPosition() + 1, endMinute[0]);
                        saveRules(preserveEnabledStates(updated,
                                KeywordMuteStore.withSchedules(updated, period, editedIds)));
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
                            keyword.getText().toString(), index < 0 || updated.get(index).enabled, selected, until[0],
                            startDay.getSelectedItemPosition() + 1, startMinute[0], endDay.getSelectedItemPosition() + 1, endMinute[0]);
                    if (index < 0) updated.add(rule); else updated.set(index, rule);
                    saveRules(updated);
                    editor.dismiss();
                    reload();
                } catch (IllegalArgumentException e) { error.setText(str("piko_mute_data_changed")); }
                catch (Exception e) { error.setText(str("piko_mute_save_error")); }
            });
            if (original != null) {
                ButtonView remove = new ButtonView(context, ButtonView.ButtonStyle.TEXT, str("piko_mute_delete"));
                remove.setTextColor(Color.rgb(244, 33, 46));
                remove.setOnClickListener(view -> {
                    try {
                        List<KeywordMuteRule> updated = new ArrayList<>(loadRules());
                        updated.removeIf(rule -> editedIds.contains(rule.id));
                        saveRules(updated);
                        editor.dismiss();
                        reload();
                    } catch (Exception e) { error.setText(str("piko_mute_save_error")); }
                });
                editor.addButton(remove);
            }
            editor.addButton(save);
            show(editor);
        }

        private void timeButton(LinearLayout form, int[] value) {
            ButtonView button = button(form, time(value[0]));
            button.setMinWidth(Theme.dpToPx(requireContext(), 80f));
            button.setMinHeight(Theme.dpToPx(requireContext(), 48f));
            button.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            button.setPadding(button.getPaddingLeft(), 0, button.getPaddingRight(), 0);
            button.setOnClickListener(view -> {
                TimePickerDialog pickerDialog = new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                    value[0] = hour * 60 + minute;
                    button.setText(time(value[0]));
                }, value[0] / 60, value[0] % 60, DateFormat.is24HourFormat(requireContext()));
                track(pickerDialog); pickerDialog.show();
            });
        }
    }

    /** Editors change text and periods only; enabled state belongs to the list switches. */
    private static List<KeywordMuteRule> preserveEnabledStates(
            List<KeywordMuteRule> current, List<KeywordMuteRule> updated) {
        java.util.Map<String, Boolean> states = new java.util.HashMap<>();
        for (KeywordMuteRule rule : current) states.put(rule.id, rule.enabled);
        List<KeywordMuteRule> result = new ArrayList<>(updated.size());
        for (KeywordMuteRule rule : updated) {
            boolean enabled = states.getOrDefault(rule.id, rule.enabled);
            result.add(enabled == rule.enabled ? rule : new KeywordMuteRule(rule.id, rule.keyword,
                    enabled, rule.mode, rule.until, rule.startDay, rule.startMinute,
                    rule.endDay, rule.endMinute, rule.groupId));
        }
        return result;
    }

    private static String day(int day) { return DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()); }
    private static String time(int minute) { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }
    private TextView label(LinearLayout form, String text) {
        TextView label = SettingsUi.summaryText(requireContext()); label.setText(text); form.addView(label); return label;
    }
    private LinearLayout section(LinearLayout form) {
        LinearLayout section = new LinearLayout(requireContext()); section.setOrientation(LinearLayout.VERTICAL);
        form.addView(section); return section;
    }
    private LinearLayout scheduleRow(LinearLayout form) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Keep the weekday at the same top inset as the period spinner. The time
        // button retains a 48dp touch target without centering the shorter spinner.
        row.setGravity(Gravity.TOP);
        row.setMinimumHeight(Theme.dpToPx(requireContext(), 48f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Theme.dpToPx(requireContext(), 6f);
        form.addView(row, params);
        return row;
    }
    private ButtonView button(LinearLayout form, String text) {
        ButtonView button = new ButtonView(requireContext(), ButtonView.ButtonStyle.TEXT, text); form.addView(button); return button;
    }
    private Spinner spinner(LinearLayout form, String[] items) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); form.addView(spinner); return spinner;
    }
}
