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
package nodomain.freeyourgadget.gadgetbridge.service.devices.moyoung;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Before;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The battery level goes through the real characteristic callbacks, because the generic
 * battery profile between them and the device support clamps values to 100.
 */
public class MoyoungBatteryLevelTest extends TestBase {
    private GBDevice device;
    private MoyoungDeviceSupport support;
    private BluetoothGattCharacteristic batteryLevel;

    @Before
    public void setUpSupport() {
        device = createDummyGDevice("00:00:00:00:00:01");
        support = new MoyoungDeviceSupport();
        support.setContext(device, BluetoothAdapter.getDefaultAdapter(), getContext());
        batteryLevel = new BluetoothGattCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
    }

    @Test
    public void levelAbove100MeansChargingAtLevelMinus100() {
        assertTrue(support.onCharacteristicChanged(null, batteryLevel, new byte[]{(byte) 175}));

        assertEquals(75, device.getBatteryLevel(0));
        assertEquals(BatteryState.BATTERY_CHARGING, device.getBatteryState(0));
    }

    @Test
    public void levelUpTo100IsNotCharging() {
        assertTrue(support.onCharacteristicChanged(null, batteryLevel, new byte[]{(byte) 75}));

        assertEquals(75, device.getBatteryLevel(0));
        assertEquals(BatteryState.BATTERY_NORMAL, device.getBatteryState(0));
    }

    @Test
    public void initialReadIsDecodedTheSameWay() {
        assertTrue(support.onCharacteristicRead(null, batteryLevel, new byte[]{(byte) 200}, BluetoothGatt.GATT_SUCCESS));

        assertEquals(100, device.getBatteryLevel(0));
        assertEquals(BatteryState.BATTERY_CHARGING, device.getBatteryState(0));
    }
}
