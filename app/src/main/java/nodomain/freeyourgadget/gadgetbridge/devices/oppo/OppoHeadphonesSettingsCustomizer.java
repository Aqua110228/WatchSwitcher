/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.oppo;

import android.os.Parcel;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.MultiSelectListPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class OppoHeadphonesSettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    public static final Creator<OppoHeadphonesSettingsCustomizer> CREATOR = new Creator<OppoHeadphonesSettingsCustomizer>() {
        @Override
        public OppoHeadphonesSettingsCustomizer createFromParcel(final Parcel in) {
            return new OppoHeadphonesSettingsCustomizer();
        }

        @Override
        public OppoHeadphonesSettingsCustomizer[] newArray(final int size) {
            return new OppoHeadphonesSettingsCustomizer[size];
        }
    };

    public OppoHeadphonesSettingsCustomizer() {
    }

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
        if (OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES.equals(preference.getKey())) {
            if (preference instanceof MultiSelectListPreference) {
                final MultiSelectListPreference ancCycleModesPref = (MultiSelectListPreference) preference;
                final Set<String> selectedValues = ancCycleModesPref.getValues();

                if (selectedValues == null || selectedValues.size() < 2) {
                    final Context context = preference.getContext();
                    final String message = context.getString(nodomain.freeyourgadget.gadgetbridge.R.string.select_at_least_option, 2);
                    new MaterialAlertDialogBuilder(context)
                        .setTitle(nodomain.freeyourgadget.gadgetbridge.R.string.warning)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
            }
        }
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }
}
