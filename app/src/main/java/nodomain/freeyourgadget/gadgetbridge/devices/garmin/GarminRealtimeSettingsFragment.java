/*  Copyright (C) 2024-2026 José Rebelo, Johannes Krude, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment;
import nodomain.freeyourgadget.gadgetbridge.adapter.SimpleIconListAdapter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.RunnableListIconItem;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ChangeRequest;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ChangeResponse;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.Date;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.EntryState;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.MenuEntry;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.RowType;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ScreenDefinition;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ScreenEntry;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ScreenState;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.SettingsService;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.SortEntry;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.Summary;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.TargetOptionEntry;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ValueFloat;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ValueList;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ValueInteger;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSettingsService.ValueDuration;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.util.XDatePreference;
import nodomain.freeyourgadget.gadgetbridge.util.XTimePreference;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MinMaxFloatWatcher;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MinMaxTextWatcher;

public class GarminRealtimeSettingsFragment extends AbstractPreferenceFragment {
    private static final Logger LOG = LoggerFactory.getLogger(GarminRealtimeSettingsFragment.class);

    public static final String EXTRA_SCREEN_ID = "screenId";
    public static final String PREF_DEBUG = "garmin_rt_debug_mode";

    public static final int ROOT_SCREEN_ID = 36352;

    private GBDevice device;
    private int screenId = ROOT_SCREEN_ID;

    private ScreenDefinition screenDefinition;
    private ScreenState screenState;

    public static final String EXTRA_PROTOBUF = "protobuf";

    public static final String ACTION_SCREEN_DEFINITION = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.screen_definition";
    public static final String ACTION_SCREEN_STATE = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.screen_state";
    public static final String ACTION_CHANGE = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.change";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                LOG.error("Got null action");
                return;
            }

            switch (action) {
                case ACTION_SCREEN_DEFINITION:
                    final ScreenDefinition incomingScreen;
                    try {
                        incomingScreen = ScreenDefinition.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for screen definition on {}", screenId, e);
                        return;
                    }
                    if (incomingScreen.getScreenId() != screenId) {
                        return;
                    }
                    LOG.debug("Got screen definition for screenId={}", screenId);
                    screenDefinition = incomingScreen;
                    break;
                case ACTION_SCREEN_STATE:
                    final ScreenState incomingState;
                    try {
                        incomingState = ScreenState.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for screen state on {}", screenId, e);
                        return;
                    }
                    if (incomingState.getScreenId() != screenId) {
                        return;
                    }
                    LOG.debug("Got screen state for screenId={}", screenId);
                    screenState = incomingState;
                    break;
                case ACTION_CHANGE:
                    final ChangeResponse incomingChange;
                    try {
                        incomingChange = ChangeResponse.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for change", e);
                        return;
                    }
                    if (incomingChange.getState().getScreenId() != screenId) {
                        return;
                    }

                    if (incomingChange.getShouldReturn()) {
                        LOG.debug("Returning from {}", screenId);
                        requireActivity().finish();
                        return;
                    }

                    LOG.debug("Got screen change for screenId={}", screenId);

                    GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
                    return;
                default:
                    LOG.error("Unknown action {}", action);
                    return;
            }

            reload();
        }
    };

    private void setDevice(final GBDevice device) {
        final Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putParcelable("device", device);
        setArguments(args);
    }

    private void setScreenId(final int screenId) {
        final Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putInt("screenId", screenId);
        setArguments(args);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final Bundle arguments = getArguments();
        if (arguments == null) {
            return;
        }
        this.device = arguments.getParcelable(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            return;
        }
        this.screenId = arguments.getInt(EXTRA_SCREEN_ID, ROOT_SCREEN_ID);
        if (screenId == 0) {
            return;
        }

        LOG.info("Opened realtime preferences screen for {}", screenId);

        getPreferenceManager().setSharedPreferencesName("garmin_rt_" + device.getAddress());
        setPreferencesFromResource(R.xml.garmin_realtime_settings, rootKey);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SCREEN_DEFINITION);
        filter.addAction(ACTION_SCREEN_STATE);
        filter.addAction(ACTION_CHANGE);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter);

        GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver);
        super.onDestroyView();
    }

    static GarminRealtimeSettingsFragment newInstance(final GBDevice device, final int screenId) {
        final GarminRealtimeSettingsFragment fragment = new GarminRealtimeSettingsFragment();
        fragment.setDevice(device);
        fragment.setScreenId(screenId);

        return fragment;
    }

    void refreshFromDevice() {
        screenDefinition = null;
        screenState = null;
        reload();
        GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
    }

    void reload() {
        final boolean debug = GBApplication.getDevicePrefs(device).getBoolean(PREF_DEBUG, BuildConfig.DEBUG);

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            LOG.error("Activity is null");
            return;
        }

        activity.invalidateOptionsMenu();

        final PreferenceScreen prefScreen = findPreference(GarminPreferences.PREF_GARMIN_REALTIME_SETTINGS);
        if (prefScreen == null) {
            LOG.error("Preference screen for {} is null", GarminPreferences.PREF_GARMIN_REALTIME_SETTINGS);
            activity.finish();
            return;
        }

        if (screenDefinition == null || screenState == null) {
            ((GarminRealtimeSettingsActivity) activity).setActionBarTitle(activity.getString(R.string.loading));

            // Disable all existing preferences while loading
            for (int i = 0; i < prefScreen.getPreferenceCount(); i++) {
                prefScreen.getPreference(i).setEnabled(false);
            }

            return;
        }

        prefScreen.removeAll();

        if (debug) {
            final Preference pref = new PreferenceCategory(activity);
            pref.setIconSpaceReserved(false);
            pref.setTitle("Screen ID: " + screenId);
            pref.setPersistent(false);
            pref.setKey("rt_pref_header_" + screenId);
            prefScreen.addPreference(pref);
        }

        // Update the screen title, if any
        if (screenDefinition.hasTitle()) {
            final String title = screenDefinition.getTitle().getText();

            ((GarminRealtimeSettingsActivity) activity).setActionBarTitle(title);
        }

        final Map<Integer, EntryState> stateById = new HashMap<>();
        for (final EntryState state : screenState.getStateList()) {
            stateById.put(state.getId(), state);
        }

        for (final ScreenEntry entry : screenDefinition.getEntryList()) {
            final EntryState state = stateById.get(entry.getId());

            final Preference pref;
            boolean supported = true;

            if (entry.hasTarget()) {
                switch (entry.getTarget().getType()) {
                    case TYPE_SUBSCREEN: // subscreen
                    case TYPE_SUBSCREEN_OPTIONS: // subscreen with options for a specific preference
                        pref = new Preference(activity);
                        pref.setOnPreferenceClickListener(preference -> {
                            final Intent newIntent = new Intent(requireContext(), GarminRealtimeSettingsActivity.class);
                            newIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                            newIntent.putExtra(GarminRealtimeSettingsActivity.EXTRA_SCREEN_ID, entry.getTarget().getSubscreen());
                            activity.startActivityForResult(newIntent, 0);
                            return true;
                        });
                        break;
                    case TYPE_LIST: // list preference
                        pref = new ListPreference(activity);
                        final CharSequence[] values = new String[entry.getTarget().getOptions().getOptionList().size()];
                        int optionIndex = 0;
                        for (final TargetOptionEntry option : entry.getTarget().getOptions().getOptionList()) {
                            values[optionIndex] = option.getTitle().getText();
                            optionIndex++;
                        }
                        final ListPreference listPreference = (ListPreference) pref;
                        listPreference.setEntries(values); // replacing % -> %% will display "%%" and not "%"
                        listPreference.setEntryValues(values);

                        Summary summary = Objects.requireNonNull(state).getSummary();
                        if (summary.hasValueList()) {
                            // max+1 is used to encode that no list value has yet been set
                            ValueList summaryList = summary.getValueList();
                            if (summaryList.hasIndex()) {
                                int index = summaryList.getIndex();
                                if (0 <= index && index < values.length) {
                                    CharSequence value = values[index];
                                    if (value != null) {
                                        listPreference.setValue(value.toString());
                                    }
                                }
                            }
                        }
                        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                            int newValueIdx = -1;
                            for (int i = 0; i < values.length; i++) {
                                if (values[i].equals(newValue.toString())) {
                                    newValueIdx = i;
                                    break;
                                }
                            }
                            if (newValueIdx < 0) {
                                LOG.error("Failed to find index for {}", newValue);
                                return false;
                            }

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setOption(ChangeRequest.Option.newBuilder()
                                                    .setIndex(newValueIdx)
                                            )
                            );
                            return true;
                        });
                        break;

                    case TYPE_TIME_OF_DAY: // time
                        pref = new XTimePreference(activity, null);
                        ((XTimePreference) pref).setValue(
                                Objects.requireNonNull(state).getSummary().getValueTimeOfDay().getSeconds() / 3600,
                                (Objects.requireNonNull(state).getSummary().getValueTimeOfDay().getSeconds() % 3600) / 60
                        );
                        if (state.getSummary().getValueTimeOfDay().hasTimeFormat()) {
                            final int timeFormat = state.getSummary().getValueTimeOfDay().getTimeFormat();
                            switch (timeFormat) {
                                case 0: // 12h
                                    ((XTimePreference) pref).setFormat(XTimePreference.Format.FORMAT_12H);
                                    break;
                                case 1: // 24h
                                    ((XTimePreference) pref).setFormat(XTimePreference.Format.FORMAT_24H);
                                    break;
                            }
                        }
                        pref.setSummary(state.getSummary().getValueDate().getSubtitle().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            final String[] pieces = newValue.toString().split(":");

                            final int hour = Integer.parseInt(pieces[0]);
                            final int minute = Integer.parseInt(pieces[1]);

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setTimeOfDay(ChangeRequest.Time.newBuilder()
                                                    .setSeconds(hour * 3600 + minute * 60)
                                            )
                            );
                            return true;
                        });
                        break;
                    case TYPE_INTEGER: {
                        final IntegerPicker integerPicker = new IntegerPicker(entry, screenId);
                        pref = integerPicker.createPreference(activity, state);
                        break;
                    }
                    case TYPE_FLOAT: {
                        final FloatPicker floatPicker = new FloatPicker(entry, screenId);
                        pref = floatPicker.createPreference(activity, state);
                        break;
                    }
                    case TYPE_DURATION: {
                        final DurationPicker durationPicker = new DurationPicker(entry, screenId);
                        pref = durationPicker.createPreference(activity, state);
                        break;
                    }
                    case TYPE_ACTIVITY: // activity
                        switch (entry.getTarget().getActivity()) {
                            case ACTIVITY_GARMIN_PAY:
                            case ACTIVITY_TEXT_RESPONSE:
                            case ACTIVITY_MUSIC_PROVIDERS:
                            case ACTIVITY_SOLAR_INTENSITY:
                            case ACTIVITY_ECG_SETUP:
                            case ACTIVITY_ECG:
                                pref = new Preference(activity);
                                pref.setVisible(debug);
                                pref.setEnabled(false);
                                break;
                            default:
                                LOG.info("unknown activity {}", entry.getTarget().getActivity());
                                supported = false;
                                pref = new Preference(activity);
                                break;
                        }

                        break;
                    case TYPE_HIDDEN: // hidden?
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case TYPE_DATE: // date picker
                        pref = new XDatePreference(activity, null);
                        ((XDatePreference) pref).setValue(
                                Objects.requireNonNull(state).getSummary().getValueDate().getCurrentDate().getYear(),
                                Objects.requireNonNull(state).getSummary().getValueDate().getCurrentDate().getMonth(),
                                Objects.requireNonNull(state).getSummary().getValueDate().getCurrentDate().getDay()
                        );
                        if (state.getSummary().getValueDate().hasMinDate()) {
                            final Calendar calendar = GregorianCalendar.getInstance();
                            calendar.set(
                                    state.getSummary().getValueDate().getMinDate().getYear(),
                                    state.getSummary().getValueDate().getMinDate().getMonth() - 1,
                                    state.getSummary().getValueDate().getMinDate().getDay()
                            );
                            ((XDatePreference) pref).setMinDate(calendar.getTimeInMillis());
                        }
                        if (state.getSummary().getValueDate().hasMaxDate()) {
                            final Calendar calendar = GregorianCalendar.getInstance();
                            calendar.set(
                                    state.getSummary().getValueDate().getMaxDate().getYear(),
                                    state.getSummary().getValueDate().getMaxDate().getMonth() - 1,
                                    state.getSummary().getValueDate().getMaxDate().getDay()
                            );
                            ((XDatePreference) pref).setMaxDate(calendar.getTimeInMillis());
                        }
                        pref.setSummary(state.getSummary().getValueDate().getSubtitle().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            final String[] pieces = newValue.toString().split("-");

                            final int year = Integer.parseInt(pieces[0]);
                            final int month = Integer.parseInt(pieces[1]);
                            final int day = Integer.parseInt(pieces[2]);

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setNewDate(ChangeRequest.NewDate.newBuilder()
                                                    .setValue(Date.newBuilder()
                                                            .setYear(year).setMonth(month).setDay(day)
                                                    )
                                            )
                            );

                            return true;
                        });
                        break;
                    case TYPE_CONNECT_IQ_STORE: // Connect IQ Store
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case TYPE_HEIGHT: // height
                        pref = new EditTextPreference(activity);
                        ((EditTextPreference) pref).setText(String.valueOf(state.getSummary().getValueHeight().getValue()));
                        ((EditTextPreference) pref).setSummary(state.getSummary().getValueHeight().getSubtitle().getText());
                        if (state.getSummary().getValueHeight().getUnit() == 0) {
                            ((EditTextPreference) pref).setDialogTitle(R.string.activity_prefs_height_cm);
                            ((EditTextPreference) pref).setTitle(R.string.activity_prefs_height_cm);
                        } else {
                            ((EditTextPreference) pref).setDialogTitle(R.string.activity_prefs_height_inches);
                            ((EditTextPreference) pref).setTitle(R.string.activity_prefs_height_inches);
                        }
                        ((EditTextPreference) pref).setOnBindEditTextListener(p -> {
                            p.setInputType(InputType.TYPE_CLASS_NUMBER);
                            p.addTextChangedListener(new MinMaxTextWatcher(p, 0, 300));
                            p.setSelection(p.getText().length());
                        });
                        ((EditTextPreference) pref).setOnPreferenceChangeListener((preference, newValue) -> {
                            final int newValueInt = Integer.parseInt(newValue.toString());

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setHeight(ChangeRequest.Height.newBuilder()
                                                    .setValue(newValueInt)
                                                    .setUnit(state.getSummary().getValueHeight().getUnit())
                                            )
                            );
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown setting type {}", entry.getTarget().getType());
                        supported = false;
                        pref = new Preference(activity);
                }
            } else { // No target
                switch (entry.getRowType()) {
                    case ROW_NOTICE: // notice
                        pref = new Preference(activity);
                        pref.setSummary(entry.getTitle().getText());
                        break;
                    case ROW_CATEGORY: // category
                        pref = new PreferenceCategory(activity);
                        break;
                    case ROW_SPACE: // space
                        pref = new PreferenceCategory(activity);
                        pref.setTitle("");
                        break;
                    case ROW_SWITCH: // switch
                        pref = new SwitchPreferenceCompat(activity);
                        pref.setLayoutResource(R.layout.preference_checkbox);
                        ((SwitchPreferenceCompat) pref).setChecked(Objects.requireNonNull(state).getSwitch().getEnabled());
                        ((SwitchPreferenceCompat) pref).setSummary(Objects.requireNonNull(state).getSwitch().getTitle().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setSwitch(ChangeRequest.Switch.newBuilder()
                                                    .setValue((Boolean) newValue)
                                            )
                            );
                            return true;
                        });

                        break;
                    case ROW_SINGLE_LINE: // single line + optional icon
                    case ROW_DOUBLE_LINE: // double line
                        pref = new Preference(activity);
                        break;
                    case ROW_SINGLE_ACTION: // single line with action (eg. glances)
                    case ROW_ACTION: // single line, normally in list for selection?
                        pref = new Preference(activity);
                        pref.setOnPreferenceClickListener(preference -> {
                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                            );
                            return true;
                        });
                        break;
                    case ROW_DEVICE: // device + status?
                    case ROW_FINISH_SETUP: // finish setup
                    case ROW_FIND_MY_DEVICE: // find my device
                    case ROW_PREFERRED_ACTIVITY_TRACKER: // preferred activity tracker
                    case ROW_HELP_AND_INFO: // help & info
                    case ROW_AVAILABLE_ACCESSORIES: // available accessories?
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case ROW_SORTABLE_AND_DELETEABLE: // sortable + delete
                        // Add all sortable items and then continue
                        for (int i = 0; i < entry.getSortOptions().getEntriesCount(); i++) {
                            final SortEntry sortEntry = entry.getSortOptions().getEntries(i);
                            final Preference sortPref = new Preference(activity);
                            final int iFinal = i;

                            final List<RunnableListIconItem> sortableOptions = new ArrayList<>(3);
                            if (i > 0) {
                                sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.widget_move_up), R.drawable.ic_arrow_upward, () -> {
                                    sortPref.setEnabled(false);
                                    sendChangeRequest(
                                            ChangeRequest.newBuilder()
                                                    .setScreenId(screenId)
                                                    .setEntryId(sortEntry.getId())
                                                    .setPosition(ChangeRequest.Position.newBuilder()
                                                            .setIndex(iFinal - 1)
                                                    )
                                    );
                                }));
                            }
                            if (i < entry.getSortOptions().getEntriesCount() - 1) {
                                sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.widget_move_down), R.drawable.ic_arrow_downward, () -> {
                                    sortPref.setEnabled(false);
                                    sendChangeRequest(
                                            ChangeRequest.newBuilder()
                                                    .setScreenId(screenId)
                                                    .setEntryId(sortEntry.getId())
                                                    .setPosition(ChangeRequest.Position.newBuilder()
                                                            .setIndex(iFinal + 1)
                                                    )
                                    );
                                }));
                            }
                            sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.appmananger_app_delete), R.drawable.ic_delete, () -> {
                                sortPref.setEnabled(false);
                                sendChangeRequest(
                                        ChangeRequest.newBuilder()
                                                .setScreenId(screenId)
                                                .setEntryId(sortEntry.getId())
                                                .setPosition(ChangeRequest.Position.newBuilder()
                                                        .setDelete(true)
                                                )
                                );
                            }));
                            final SimpleIconListAdapter sortOptionsAdapter = new SimpleIconListAdapter(activity, sortableOptions);
                            sortPref.setTitle(sortEntry.getTitle().getText());
                            sortPref.setPersistent(false);
                            sortPref.setIconSpaceReserved(false);
                            sortPref.setKey("rt_pref_" + screenId + "_" + entry.getId() + "__" + sortEntry.getId());
                            sortPref.setOnPreferenceClickListener(preference -> {
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle(sortPref.getTitle())
                                        .setAdapter(sortOptionsAdapter, (dialogInterface, j) -> sortableOptions.get(j).getAction().run())
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .create().show();
                                return true;
                            });
                            prefScreen.addPreference(sortPref);
                        }

                        continue; // We already added all options above, continue
                    case ROW_TEXT: // text
                        pref = new EditTextPreference(activity);

                        ((EditTextPreference) pref).setOnBindEditTextListener(p -> {
                            int maxValue = Integer.MAX_VALUE;
                            if (entry.getTextOption().hasLimits() && entry.getTextOption().getLimits().hasMaxLength()) {
                                p.setFilters(new InputFilter[]{new InputFilter.LengthFilter(entry.getTextOption().getLimits().getMaxLength())});
                            }
                            p.setSelection(p.getText().length());
                        });
                        ((EditTextPreference) pref).setOnPreferenceChangeListener((preference, newValue) -> {
                            if (StringUtils.isNullOrEmpty(newValue.toString())) {
                                return true;
                            }
                            pref.setEnabled(false);
                            sendChangeRequest(
                                    ChangeRequest.newBuilder()
                                            .setScreenId(screenId)
                                            .setEntryId(entry.getId())
                                            .setText(ChangeRequest.Text.newBuilder()
                                                    .setValue(newValue.toString())
                                            )
                            );
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown row type {}", entry.getRowType());
                        supported = false;
                        pref = new Preference(activity);
                }
            }

            if (StringUtils.isNullOrEmpty(pref.getTitle())
                    && entry.getRowType() != RowType.ROW_NOTICE
                    && entry.getRowType() != RowType.ROW_SPACE) {
                pref.setTitle(!StringUtils.isEmpty(entry.getTitle().getText()) ? entry.getTitle().getText() : activity.getString(R.string.unknown));

                if (pref instanceof DialogPreference) {
                    ((DialogPreference) pref).setDialogTitle(pref.getTitle());
                }
            }

            final int icon = getIcon(entry);
            if (icon != 0) {
                pref.setIcon(icon);
            } else {
                pref.setIconSpaceReserved(false);
            }

            if (state != null && !StringUtils.isEmpty(state.getSummary().getTitle().getText())) {
                pref.setSummary(state.getSummary().getTitle().getText().replace("%", "%%"));
            }

            if (state != null && state.hasState()) {
                switch (state.getState()) {
                    case 1:
                        pref.setVisible(false);
                        break;
                    case 2:
                        pref.setEnabled(false);
                        break;
                    default:
                        LOG.warn("Unknown state value {}", state.getState());
                }
            }

            if (!supported) {
                pref.setEnabled(false);

                if (StringUtils.isNullOrEmpty(pref.getSummary())) {
                    pref.setSummary(R.string.unsupported);
                } else {
                    pref.setSummary(activity.getString(R.string.menuitem_unsupported, pref.getSummary()));
                }
            }

            if (debug) {
                final StringBuilder sb = new StringBuilder();

                if (pref.getSummary() != null && pref.getSummary().length() != 0) {
                    sb.append(pref.getSummary()).append("\n");
                }

                sb.append("id=").append(entry.getId());
                sb.append(", type=").append(entry.getRowType());

                if (icon == 0 && entry.hasIcon()) {
                    sb.append(", icon=").append(entry.getIcon());
                }

                if (entry.hasTarget()) {
                    sb.append(", targetType=").append(entry.getTarget().getType());
                    if (entry.getTarget().hasActivity()) {
                        sb.append(", targetActivity=").append(entry.getTarget().getActivity());
                    }
                }

                // when using pref.setSummary(value), values containing percent signs (%) can
                // cause UnknownFormatConversionException in java.util.Formatter. For example:
                // setting System / Backlight / Brightness with values like "50%" and "75%"
                pref.setSummaryProvider(new PlainFormatter(sb.toString()));
            }

            pref.setPersistent(false);
            pref.setKey("rt_pref_" + screenId + "_" + entry.getId());
            prefScreen.addPreference(pref);
        }

        // If no preferences after the last visible preference category are visible, hide it
        boolean previousWasVisible = false;
        PreferenceCategory lastSeenCategory = null;
        for (int i = prefScreen.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference pref = prefScreen.getPreference(i);
            if (pref instanceof PreferenceCategory) {
                lastSeenCategory = (PreferenceCategory) pref;

                if (!previousWasVisible) {
                    lastSeenCategory.setVisible(false);
                }

                previousWasVisible = false;
            } else {
                previousWasVisible |= pref.isVisible();
            }
        }

        if (!previousWasVisible && lastSeenCategory != null) {
            lastSeenCategory.setVisible(false);
        }
    }

    void populateMenu(final Menu menu) {
        if (screenDefinition == null) {
            return;
        }

        final boolean debug = GBApplication.getDevicePrefs(device).getBoolean(PREF_DEBUG, BuildConfig.DEBUG);

        for (final MenuEntry menuEntry : screenDefinition.getMenuEntryList()) {
            final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, menuEntry.getLabel().getText());
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            boolean supported = true;

            if (menuEntry.hasTarget()) {
                switch (menuEntry.getTarget().getType()) {
                    case TYPE_SUBSCREEN: // subscreen
                    case TYPE_SUBSCREEN_OPTIONS: // subscreen with options for a specific preference
                        menuItem.setOnMenuItemClickListener(item -> {
                            final Intent newIntent = new Intent(requireContext(), GarminRealtimeSettingsActivity.class);
                            newIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                            newIntent.putExtra(GarminRealtimeSettingsActivity.EXTRA_SCREEN_ID, menuEntry.getTarget().getSubscreen());
                            requireActivity().startActivityForResult(newIntent, 0);
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown menu target type {}", menuEntry.getTarget().getType());
                        supported = false;
                        break;
                }
            } else {
                LOG.info("Menu entry {} has no target", menuEntry.getLabel().getText());
                supported = false;
            }

            if (!supported) {
                menuItem.setEnabled(false);
                menuItem.setVisible(debug);
            }
        }
    }

    private final class IntegerPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final ScreenEntry integerEntry;
        private final int integerScreenId;

        private IntegerPicker(@NonNull ScreenEntry integerEntry, int integerScreenId) {
            this.integerEntry = integerEntry;
            this.integerScreenId = integerScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            int minValue = Integer.MIN_VALUE;
            int maxValue = Integer.MAX_VALUE;
            if (integerEntry.getTarget().getIntegerPicker().hasMin()) {
                minValue = integerEntry.getTarget().getIntegerPicker().getMin();
            }
            if (integerEntry.getTarget().getIntegerPicker().hasMax()) {
                maxValue = integerEntry.getTarget().getIntegerPicker().getMax();
            }
            editText.addTextChangedListener(new MinMaxTextWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final int newValueInt = Integer.parseInt(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                    ChangeRequest.newBuilder()
                            .setScreenId(integerScreenId)
                            .setEntryId(integerEntry.getId())
                            .setIntegerValue(ChangeRequest.IntegerValue.newBuilder()
                                    .setValue(newValueInt)
                            )
            );
            return true;
        }

        Preference createPreference(FragmentActivity activity, EntryState state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final ValueInteger summaryValue = state.getSummary().getValueInteger();
            preference.setText(String.valueOf(summaryValue.getValue()));
            preference.setSummary(summaryValue.getFormatted().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    private final class DurationPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final ScreenEntry durationEntry;
        private final int durationScreenId;

        private DurationPicker(@NonNull ScreenEntry durationEntry, int durationScreenId) {
            this.durationEntry = durationEntry;
            this.durationScreenId = durationScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            int minValue = 0;
            int maxValue = Integer.MAX_VALUE;
            if (durationEntry.getTarget().getDurationOptions().hasMinSeconds()) {
                minValue = durationEntry.getTarget().getDurationOptions().getMinSeconds();
            }
            if (durationEntry.getTarget().getDurationOptions().hasMaxSeconds()) {
                maxValue = durationEntry.getTarget().getDurationOptions().getMaxSeconds();
            }
            editText.addTextChangedListener(new MinMaxTextWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final int newSeconds = Integer.parseInt(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                    ChangeRequest.newBuilder()
                            .setScreenId(durationScreenId)
                            .setEntryId(durationEntry.getId())
                            .setDuration(ChangeRequest.Time.newBuilder()
                                    .setSeconds(newSeconds)
                            )
            );
            return true;
        }

        Preference createPreference(FragmentActivity activity, EntryState state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final ValueDuration summaryValue = state.getSummary().getValueDuration();
            preference.setText(String.valueOf(summaryValue.getSeconds()));
            preference.setSummary(summaryValue.getFormatted().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    private final class FloatPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final ScreenEntry floatEntry;
        private final int floatScreenId;

        private FloatPicker(@NonNull ScreenEntry floatEntry, int floatScreenId) {
            this.floatEntry = floatEntry;
            this.floatScreenId = floatScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            float minValue = Float.MIN_VALUE;
            float maxValue = Float.MAX_VALUE;
            if (floatEntry.getTarget().getFloatOptions().hasMinValue()) {
                minValue = floatEntry.getTarget().getFloatOptions().getMinValue();
            }
            if (floatEntry.getTarget().getFloatOptions().hasMaxValue()) {
                maxValue = floatEntry.getTarget().getFloatOptions().getMaxValue();
            }
            editText.addTextChangedListener(new MinMaxFloatWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final float newFloat = Float.parseFloat(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                    ChangeRequest.newBuilder()
                            .setScreenId(floatScreenId)
                            .setEntryId(floatEntry.getId())
                            .setFloatValue(ChangeRequest.FloatValue.newBuilder()
                                    .setValue(newFloat)
                            )
            );
            return true;
        }

        EditTextPreference createPreference(FragmentActivity activity, EntryState state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final ValueFloat summaryValue = state.getSummary().getValueFloat();
            preference.setText(String.valueOf(summaryValue.getValue()));
            preference.setSummary(summaryValue.getFormatted().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    @DrawableRes
    private int getIcon(final ScreenEntry entry) {
        if (entry.hasIcon()) {
            switch (entry.getIcon()) {
                //
                // Main menu
                case ICON_GARMIN_PAY: // Garmin Pay
                    return R.drawable.ic_credit_card;
                case ICON_TEXT_RESPONSES: // Text Responses
                    return R.drawable.ic_reply;
                case ICON_CLOCKS: // Clocks
                    return R.drawable.ic_access_time;
                case ICON_GLANCES: // Glances
                    return R.drawable.ic_widgets;
                case ICON_CONTROLS: // Controls
                    return R.drawable.ic_menu;
                case ICON_ACTIVITIES_AND_APPS: // Activities / Apps, have the same icon
                    return R.drawable.ic_activity_unknown_small;
                case ICON_SHORTCUT: // Shortcut
                    return R.drawable.ic_shortcut;
                case ICON_NOTIFICATIONS_AND_ALERTS: // Notifications & Alerts
                    return R.drawable.ic_notifications;
                case ICON_WRIST_HEARTRATE: // Wrist heart rate frequency
                    return R.drawable.ic_heartrate;
                case ICON_ALARMS: // Alarms
                    return R.drawable.ic_access_alarms;
                case ICON_SENSORS_AND_ACCESSORIES: // Sensors & accessories
                case ICON_WATCH_SENSORS: // Watch Sensors
                    return R.drawable.ic_sensor_calibration;
                case ICON_ACCESSORIES: // Accessories
                    return R.drawable.ic_bluetooth_searching;
                case ICON_MAP: // Map
                    return R.drawable.ic_map;
                case ICON_MUSIC: // Music
                    return R.drawable.ic_music_note;
                case ICON_PHONE: // Phone
                    return R.drawable.ic_phone;
                case ICON_CONNECTIVITY: // Connectivity
                    return R.drawable.ic_bluetooth_searching;
                case ICON_AUDIO_PROMPTS: // Audio Prompts
                case ICON_SOUND_AND_VIBE: // Sound & Vibe
                    return R.drawable.ic_volume_up;
                case ICON_DISPLAY_AND_BRIGHTNESS: // Display & Brightness
                    return R.drawable.ic_wb_sunny;
                case ICON_FOCUS_MODES: // Focus Modes
                    return R.drawable.ic_focus;
                case ICON_USER_PROFILE: // User Profile
                    return R.drawable.ic_person;
                case ICON_SAFETY_AND_TRACKING: // Safety & Tracking
                    return R.drawable.ic_emergency;
                case ICON_ACTIVITY_TRACKING: // Activity Tracking
                    return R.drawable.ic_activity_unknown_small;
                case ICON_NAVIGATION: // Navigation
                    return R.drawable.ic_navigation;
                case ICON_POWER_MANAGER: // Power manager
                    return R.drawable.ic_battery;
                case ICON_SYSTEM: // System
                    return R.drawable.ic_settings;
                case ICON_SOLAR: // Solar
                    return R.drawable.ic_wb_sunny;
                case ICON_APPEARANCE: // Appearance
                    return R.drawable.ic_paint;
                case ICON_HEALTH_AND_WELLNESS: // Health & wellness
                    return R.drawable.ic_health;
                case ICON_ACCESSIBILITY: // Accessibility
                    return R.drawable.ic_accessibility_new;

                //
                // Sortable screens (glances, apps, etc)
                case ICON_ACTION_ADD:
                    return R.drawable.ic_add_gray;
                case ICON_ACTION_REMOVE:
                    return R.drawable.ic_remove;
                case ICON_INREACH_TRACKING: // inReach tracking
                    return R.drawable.ic_share_location;
                case ICON_INREACH_REMOTE: // inReach remote
                    return R.drawable.ic_settings_remote;
                case ICON_SOUND_SETTINGS: // sound settings
                    return R.drawable.ic_notifications_active;
                case ICON_DISPLAY:
                    return R.drawable.ic_device_display;
                default:
                    LOG.info("no icon mapping found for: {}", entry.getIcon());
                    return 0;
            }
        }

        return 0;
    }

    void toggleDebug() {
        final Prefs prefs = GBApplication.getDevicePrefs(device);
        prefs.getPreferences().edit()
                .putBoolean(PREF_DEBUG, !prefs.getBoolean(PREF_DEBUG, BuildConfig.DEBUG))
                .apply();

        reload();
    }

    void shareDebug() {
        final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");

        final StringBuilder sb = new StringBuilder();

        sb.append("screenId: ").append(screenId);
        sb.append("\n");

        sb.append("settingsScreen: ");
        if (screenDefinition != null) {
            sb.append(GB.hexdump(screenDefinition.toByteArray()));
        } else {
            sb.append("null");
        }
        sb.append("\n");

        sb.append("settingsState: ");
        if (screenState != null) {
            sb.append(GB.hexdump(screenState.toByteArray()));
        } else {
            sb.append("null");
        }
        sb.append("\n");

        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Garmin Settings Screen " + screenId);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());

        try {
            startActivity(Intent.createChooser(intent, "Share debug info"));
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Failed to share text", Toast.LENGTH_LONG).show();
        }
    }

    private void sendChangeRequest(final ChangeRequest.Builder changeRequest) {
        screenDefinition = null;
        screenState = null;
        final Smart smart = Smart.newBuilder()
                .setSettingsService(SettingsService.newBuilder()
                        .setChangeRequest(changeRequest)
                ).build();
        GBApplication.deviceService(device).onSendConfiguration("protobuf:" + GB.hexdump(smart.toByteArray()));
    }

    private static record PlainFormatter(CharSequence value) implements Preference.SummaryProvider {
        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull Preference preference) {
            return value;
        }
    }
}
