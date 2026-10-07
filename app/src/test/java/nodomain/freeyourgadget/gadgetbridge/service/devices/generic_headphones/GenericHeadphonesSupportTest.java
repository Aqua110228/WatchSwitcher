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
package nodomain.freeyourgadget.gadgetbridge.service.devices.generic_headphones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowBluetoothAdapter;
import org.robolectric.shadows.ShadowLooper;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The phone's headset service is available whether or not a given pair of headphones is
 * connected, so its arrival is not a connection. The device should only read as connected while
 * the headphones actually are, and follow them when they connect later.
 */
public class GenericHeadphonesSupportTest extends TestBase {
    private static final String ADDRESS = "00:00:00:00:00:31";

    private GBDevice device;
    private GenericHeadphonesSupport support;
    private BluetoothProfile headsetService;
    private ShadowBluetoothAdapter adapter;

    @Before
    public void setUpSupport() {
        device = createDummyGDevice(ADDRESS);
        headsetService = mock(BluetoothProfile.class);
        adapter = shadowOf(BluetoothAdapter.getDefaultAdapter());
        adapter.setProfileProxy(BluetoothProfile.HEADSET, headsetService);
        support = new GenericHeadphonesSupport();
        support.setContext(device, BluetoothAdapter.getDefaultAdapter(), getContext());
    }

    @After
    public void disposeSupport() {
        support.dispose();
    }

    private void headphonesAre(final int state) {
        when(headsetService.getConnectionState(any())).thenReturn(state);
    }

    private void connect() {
        support.connect();
        ShadowLooper.shadowMainLooper().idle();
    }

    private void broadcastHeadsetState(final String address, final int state) {
        final Intent intent = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address));
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        getContext().sendBroadcast(intent);
        ShadowLooper.shadowMainLooper().idle();
    }

    private static int receiversFor(final String action) {
        int count = 0;
        final ShadowApplication app = shadowOf(RuntimeEnvironment.getApplication());
        for (final ShadowApplication.Wrapper wrapper : app.getRegisteredReceivers()) {
            if (wrapper.getIntentFilter().hasAction(action)) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void notConnectedWhileTheHeadphonesAreNot() {
        headphonesAre(BluetoothProfile.STATE_DISCONNECTED);
        connect();
        assertEquals(GBDevice.State.WAITING_FOR_RECONNECT, device.getState());
    }

    @Test
    public void connectedWhenTheHeadphonesAre() {
        headphonesAre(BluetoothProfile.STATE_CONNECTED);
        connect();
        assertEquals(GBDevice.State.INITIALIZED, device.getState());
    }

    @Test
    public void followsTheHeadphonesWhenTheyConnectLater() {
        headphonesAre(BluetoothProfile.STATE_DISCONNECTED);
        connect();

        // someone else's headphones connecting changes nothing
        broadcastHeadsetState("00:00:00:00:00:99", BluetoothProfile.STATE_CONNECTED);
        assertEquals(GBDevice.State.WAITING_FOR_RECONNECT, device.getState());

        broadcastHeadsetState(ADDRESS, BluetoothProfile.STATE_CONNECTED);
        assertEquals(GBDevice.State.INITIALIZED, device.getState());
    }

    @Test
    public void releasesTheHeadsetServiceWhenDisposed() {
        headphonesAre(BluetoothProfile.STATE_CONNECTED);
        connect();
        assertTrue(adapter.hasActiveProfileProxy(BluetoothProfile.HEADSET));

        support.dispose();
        assertFalse(adapter.hasActiveProfileProxy(BluetoothProfile.HEADSET));
    }

    @Test
    public void reconnectingWhileWaitingRegistersNothingTwice() {
        headphonesAre(BluetoothProfile.STATE_DISCONNECTED);
        // receivers declared in the manifest already listen for some of these, so count what connecting adds
        final int headsetBefore = receiversFor(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        final int disconnectBefore = receiversFor(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        connect();
        connect();

        assertEquals(headsetBefore + 1, receiversFor(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
        assertEquals(disconnectBefore + 1, receiversFor(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }
}
