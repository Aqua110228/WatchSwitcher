/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze;

import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.Beyond3ProSupport;

/**
 * Zeblaze Beyond 3 Pro.
 *
 * The device's official app talks to it over its own unencrypted,
 * unauthenticated "ZH_SDK" GATT protocol (see {@link ZeblazeConstants} and
 * {@link Beyond3ProSupport}), not any Xiaomi/Mi Fitness protocol, despite the
 * GATT service sharing Xiaomi's custom UUID base. No BLE pairing/bonding is
 * used by the official app either.
 */
public class Beyond3ProCoordinator extends AbstractBLEDeviceCoordinator {
    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        final ParcelUuid service = new ParcelUuid(ZeblazeConstants.UUID_SERVICE_ZH_SDK);
        return Collections.singletonList(new ScanFilter.Builder().setServiceUuid(service).build());
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Beyond 3 pro_[A-Z0-9]{4}$");
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Zeblaze";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return Beyond3ProSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_zeblaze_beyond_3_pro;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.WATCH;
    }
}
