/*  Copyright (C) 2026 Arjan Schrijver

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

public class C20ProCoordinator extends AbstractMoyoungDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^C20_Pro$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_c20_pro;
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
        return 508;
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public int getAlarmSlotCount(final GBDevice device) {
        return 8;
    }

    @Override
    public boolean newAlarmProtocol() {
        return true;
    }
}
