/*  Copyright (C) 2019-2024 Andreas Shimokawa, José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import androidx.preference.PreferenceFragmentCompat;

import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;

import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class DeviceSettingsActivity extends AbstractSettingsActivityV2 {
    public static final String MENU_ENTRY_POINT = "MENU_ENTRY_POINT";

    public enum MENU_ENTRY_POINTS {
        DEVICE_SETTINGS,
        AUTH_SETTINGS,
        APPLICATION_SETTINGS
    }

    @Override
    protected PreferenceFragmentCompat newFragment() {
        final GBDevice device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        final MENU_ENTRY_POINTS menu_entry = (MENU_ENTRY_POINTS) getIntent().getSerializableExtra(MENU_ENTRY_POINT);

        return DeviceSpecificSettingsFragment.newInstance(device, menu_entry);
    }

    @Override
    public void onSearchResultClicked(final SearchPreferenceResult result) {
        final GBDevice device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        final MENU_ENTRY_POINTS menu_entry = (MENU_ENTRY_POINTS) getIntent().getSerializableExtra(MENU_ENTRY_POINT);
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final DeviceSettingsSpec modelSpec = coordinator.getDeviceSettings(device);

        if (modelSpec != null && modelSpec.containsKey(result.getKey())) {
            // A preference indexed programmatically by DeviceSettingsIndexer - resolve the screen
            // that holds it directly from the model, since it has no XML-derived result.getScreen().
            navigateToSearchResult(result, modelSpec.findScreenKeyForPreference(result.getKey()));
            return;
        }

        if (result.getScreen() != null) {
            // A nested PreferenceScreen inside an indexed XML file - the library already resolved it.
            navigateToSearchResult(result, result.getScreen());
            return;
        }

        final DeviceSpecificSettings deviceSpecificSettings = DeviceSpecificSettingsFragment.buildDeviceSpecificSettings(device, menu_entry);
        final String rootScreenForSubScreen = deviceSpecificSettings.getRootScreenForSubScreen(result.getResourceFile());
        if (rootScreenForSubScreen != null) {
            navigateToSearchResult(result, rootScreenForSubScreen);
            return;
        }

        super.onSearchResultClicked(result);
    }
}
