/*  Copyright (C) 2019-2026 Andreas Shimokawa, Daniel Dakhno, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.btle.actions;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BtLEAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCallback;

/// Calls {@link BluetoothGatt#requestMtu(int)}. Results are returned to
/// {@link GattCallback#onMtuChanged(BluetoothGatt, int, int)}
public class RequestMtuAction extends BtLEAction {
    private static final Logger LOG = LoggerFactory.getLogger(RequestMtuAction.class);

    private final int mtu;
    private final AbstractBTLEDeviceSupport deviceSupport;
    private final int deviceIdx;

    public RequestMtuAction(@IntRange(from = 23L, to = 517L) final int mtu,
                            @NonNull AbstractBTLEDeviceSupport deviceSupport,
                            @IntRange(from = 23L) final int deviceIdx) {
        super(null);
        this.mtu = mtu;
        this.deviceSupport = deviceSupport;
        this.deviceIdx = deviceIdx;
    }


    @Override
    public boolean expectsResult() {
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean run(@NonNull final BluetoothGatt gatt) {
        int currentMtu = deviceSupport.getMTU(deviceIdx);

        int request = mtu;

        // see 3.4.2.1 Exchange MTU Request (Blueooth Core Specification v.5.0.0, vol 3, Part F)
        // on error: re-request current MTU instead of aborting the whole Transaction

        if (currentMtu >= mtu) {
            request = currentMtu;
            LOG.info("requested MTU {} >= effective MTU {}: re-requesting {}", mtu, currentMtu);
        } else if (currentMtu > 23) {
            request = currentMtu;
            LOG.warn("MTU has already been negotiated to {}, not requesting MTU {} to avoid GATT_INVALID_PDU: re-requesting: {}",
                    currentMtu, mtu, request);
        }

        return gatt.requestMtu(request);
    }

    @NonNull
    @Override
    public String toString() {
        return getCreationTime() + " " + getClass().getSimpleName() + " mtu=" + mtu;
    }
}
