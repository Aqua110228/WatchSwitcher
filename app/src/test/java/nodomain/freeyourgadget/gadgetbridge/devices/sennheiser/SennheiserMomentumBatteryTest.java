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
package nodomain.freeyourgadget.gadgetbridge.devices.sennheiser;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager;
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothStateChangeReceiver;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class SennheiserMomentumBatteryTest extends TestBase {
    private static final String ADDRESS = "00:11:22:33:44:55";

    @SuppressWarnings("unchecked")
    private static void register(final GBDevice device) throws Exception {
        final DeviceManager deviceManager = GBApplication.app().getDeviceManager();
        final Field field = DeviceManager.class.getDeclaredField("deviceList");
        field.setAccessible(true);
        final List<GBDevice> deviceList = (List<GBDevice>) field.get(deviceManager);
        deviceList.clear();
        deviceList.add(device);
    }

    @Test
    public void takesTheBatteryLevelAndroidReports() throws Exception {
        final GBDevice device = new GBDevice(ADDRESS, "MOMENTUM M2 IEBT", null, null, DeviceType.SENNHEISER_MOMENTUM_IN_EAR_WIRELESS);
        device.setState(GBDevice.State.INITIALIZED);
        register(device);

        final BluetoothDevice headset = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADDRESS);
        new BluetoothStateChangeReceiver().onReceive(getContext(), new Intent(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_ACTION_BATTERY_LEVEL_CHANGED)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, headset)
                .putExtra(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_EXTRA_BATTERY_LEVEL, 70));

        assertEquals(70, device.getBatteryLevel(0));
    }
}
