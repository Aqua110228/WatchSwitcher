/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * MT55 - a MOYOUNG-V2 watch sold unbranded / under various names.
 *
 * Identification confirmed over GATT (personal observation):
 *   Manufacturer (0x2A29) = "MOYOUNG-V2"     -> V2 protocol, MTU-based framing
 *   Software rev (0x2A28) = "MOY-JSB2-2.0.4"
 *
 * Tested on hardware via Gadgetbridge: pairs by name, connects, and syncs
 * battery, steps, distance, sleep (with REM stages), workouts and live heart
 * rate. Only capabilities confirmed on the device are declared here.
 *
 * Note: MT55 is dual-mode (BR/EDR + LE). Do NOT pair it in Android's system
 * Bluetooth settings - if Android bonds it as an audio device, A2DP/GATT
 * contention stalls the activity fetch. Pair only within Gadgetbridge.
 */
public class MT55Coordinator extends AbstractMoyoungDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^MT55$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_mt55;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_banglejs;
    }

    @Override
    public String getManufacturer() {
        return "Mo Young / Da Fit";
    }

    @Override
    public int getMtu() {
        // MOYOUNG-V2 -> MTU-based framing.
        return 508;
    }

    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        // Confirmed: sleep sync decodes light/deep/REM stages on this device.
        return true;
    }

    @Override
    public boolean supportsHeartRateStreaming() {
        // Confirmed: live heart-rate measurement works on this device.
        return true;
    }
}
