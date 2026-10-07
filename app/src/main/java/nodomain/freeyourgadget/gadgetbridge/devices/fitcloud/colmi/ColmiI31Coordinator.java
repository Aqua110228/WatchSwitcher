/*  Copyright (C) 2026 Tassio

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
package nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.colmi;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.ak102.Ak102Coordinator;

/**
 * Colmi i31.
 *
 * The Colmi i31 is a FitCloud/TopStep-family watch (Realtek RTL8763EWE, companion app
 * FitCloudPro) and speaks the identical two-layer BLE protocol as the {@code AK102}, so the
 * whole protocol layer ({@code Ak102DeviceSupport}, {@code Ak102SyncParser},
 * {@code Ak102Constants}, the sample providers and settings customizer) is reused as-is.
 * The concrete per-device capability surface is derived at runtime from the device-info
 * feature blob persisted at connect time (see {@code supportsWatchFeature} in the base
 * coordinator), so the only thing that differs from the AK102 is the advertised name it
 * matches and the display name.
 */
public class ColmiI31Coordinator extends Ak102Coordinator {

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^i31", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_colmi_i31;
    }

    @Override
    public String getManufacturer() {
        return "Colmi";
    }
}