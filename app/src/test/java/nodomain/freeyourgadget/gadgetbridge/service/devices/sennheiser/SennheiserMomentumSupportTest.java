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
package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowBluetoothDevice;
import org.robolectric.shadows.ShadowLooper;

import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothStateChangeReceiver;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.BtBRAction;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.actions.WriteAction;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Android announces the battery level when the hands-free link comes up, usually before the GAIA
 * connection is established, and does not repeat it until the level changes.
 */
public class SennheiserMomentumSupportTest extends TestBase {
    private static final String ADDRESS = "00:11:22:33:44:66";

    private GBDevice device;
    private SennheiserMomentumSupport support;

    @Before
    public void setUpSupport() {
        device = new GBDevice(ADDRESS, "MOMENTUM M2 IEBT", null, null, DeviceType.SENNHEISER_MOMENTUM_IN_EAR_WIRELESS);
        device.setState(GBDevice.State.WAITING_FOR_RECONNECT);
        support = new SennheiserMomentumSupport();
        support.setContext(device, BluetoothAdapter.getDefaultAdapter(), getContext());
    }

    @After
    public void disposeSupport() {
        support.dispose();
    }

    private void androidAnnouncesBattery(final String address, final int level) {
        getContext().sendBroadcast(new Intent(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_ACTION_BATTERY_LEVEL_CHANGED)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address))
                .putExtra(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_EXTRA_BATTERY_LEVEL, level));
        ShadowLooper.shadowMainLooper().idle();
    }

    /** Runs what initialization would do once connected, apart from writing to the headset. */
    private void initialize() {
        final TransactionBuilder builder = support.createTransactionBuilder("initialize");
        support.initializeDevice(builder);
        for (final BtBRAction action : builder.getTransaction().getActions()) {
            if (!(action instanceof WriteAction)) {
                action.run(null);
            }
        }
    }

    private void bondStateIs(final int bondState) {
        final ShadowBluetoothDevice headset = shadowOf(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADDRESS));
        headset.setBondState(bondState);
    }

    @Test
    public void doesNotConnectWhileTheHeadsetIsBeingPaired() {
        bondStateIs(BluetoothDevice.BOND_BONDING);
        assertFalse(support.isBonded());
        assertFalse("connected while still pairing", support.connect());
    }

    @Test
    public void doesNotConnectWhileTheHeadsetIsNotPaired() {
        bondStateIs(BluetoothDevice.BOND_NONE);
        assertFalse(support.isBonded());
        assertFalse("connected while unpaired", support.connect());
    }

    @Test
    public void connectsOnceTheHeadsetIsPaired() {
        bondStateIs(BluetoothDevice.BOND_BONDED);
        assertTrue(support.isBonded());
    }

    @Test
    public void appliesTheLevelAnnouncedBeforeConnecting() {
        androidAnnouncesBattery(ADDRESS, 70);
        initialize();
        assertEquals(GBDevice.State.INITIALIZED, device.getState());
        assertEquals(70, device.getBatteryLevel(0));
    }

    @Test
    public void ignoresOtherDevicesLevels() {
        androidAnnouncesBattery("00:11:22:33:44:77", 70);
        initialize();
        assertEquals(GBDevice.BATTERY_UNKNOWN, device.getBatteryLevel(0));
    }
}
