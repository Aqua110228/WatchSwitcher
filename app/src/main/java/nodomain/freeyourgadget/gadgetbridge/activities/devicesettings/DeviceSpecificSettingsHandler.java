/*  Copyright (C) 2021-2024 José Rebelo

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * A device-specific preference handler, that allows for {@link nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator}s to register
 * their own preferences dynamically.
 * <p>
 * This is the device-bound specialisation of {@link SettingsRenderHost} -- see that interface for
 * the full render-host contract shared with non-device settings hosts.
 */
public interface DeviceSpecificSettingsHandler extends SettingsRenderHost {
    /**
     * Get the device associated with this {@link DeviceSpecificSettingsHandler}.
     *
     * @return the {@link GBDevice}.
     */
    @NonNull
    @Override
    GBDevice getDevice();

    /**
     * Returns the current {@link PreferenceScreen}, if available.
     */
    @Nullable
    PreferenceScreen getPreferenceScreen();
}
