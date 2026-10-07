/*  Copyright (C) 2024-2026 Arjan Schrijver, José Rebelo

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardWidgetsActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class DashboardPreferencesActivity extends AbstractSettingsActivityV2 {
    @Override
    protected PreferenceFragmentCompat newFragment() {
        return new DashboardPreferencesFragment();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public static class DashboardPreferencesFragment extends AbstractPreferenceFragment {
        /**
         * Preferences on this screen that affects the dashboard's rendering (not an individual widget instance's).
         */
        private static final List<String> DASHBOARD_WIDE_PREFS = List.of(
                "dashboard_cards_enabled",
                "dashboard_devices_all",
                "dashboard_devices_multiselect"
        );

        private final SharedPreferences.OnSharedPreferenceChangeListener changeListener =
                (sharedPreferences, key) -> {
                    if (DASHBOARD_WIDE_PREFS.contains(key)) {
                        sendDashboardConfigChangedIntent();
                    }
                };

        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.dashboard_preferences, rootKey);

            final MultiSelectListPreference dashboardDevices = findPreference("dashboard_devices_multiselect");
            if (dashboardDevices != null) {
                final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
                final List<String> deviceMACs = new ArrayList<>();
                final List<String> deviceNames = new ArrayList<>();
                for (GBDevice dev : devices) {
                    deviceMACs.add(dev.getAddress());
                    deviceNames.add(dev.getAliasOrName());
                }
                dashboardDevices.setEntryValues(deviceMACs.toArray(new String[0]));
                dashboardDevices.setEntries(deviceNames.toArray(new String[0]));
            }

            final Preference manageWidgets = findPreference("pref_dashboard_manage_widgets");
            if (manageWidgets != null) {
                manageWidgets.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), DashboardWidgetsActivity.class));
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            Objects.requireNonNull(getPreferenceManager().getSharedPreferences())
                    .registerOnSharedPreferenceChangeListener(changeListener);
        }

        @Override
        public void onPause() {
            Objects.requireNonNull(getPreferenceManager().getSharedPreferences())
                    .unregisterOnSharedPreferenceChangeListener(changeListener);
            super.onPause();
        }

        /**
         * Signal dashboard that its config has changed
         */
        private void sendDashboardConfigChangedIntent() {
            final Intent intent = new Intent();
            intent.setAction(DashboardFragment.ACTION_CONFIG_CHANGE);
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
        }
    }
}
