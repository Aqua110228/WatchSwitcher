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

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_GET_APPLICATION_VERSION;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_GET_LED_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_GET_SPEECH_RECOGNITION_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_GET_VOICE_PROMPT_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_GET_VOLUME_ORIENTATION;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_POWER_OFF;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_SET_LED_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_SET_SPEECH_RECOGNITION_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_SET_VOICE_PROMPT_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumProtocol.CMD_SET_VOLUME_ORIENTATION;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothStateChangeReceiver;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

public class SennheiserMomentumSupport extends AbstractHeadphoneBTBRDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SennheiserMomentumSupport.class);

    // The headset offers GAIA as a serial port service; its RFCOMM channel changes between sessions,
    // so it is always looked up by this UUID rather than by channel number.
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final SennheiserMomentumProtocol protocol = new SennheiserMomentumProtocol();

    // Android announces the headset's battery level when the hands-free link comes up, which is
    // usually before this connection is established, and does not repeat it until the level changes.
    // Keep the latest announcement so it can be applied once connected.
    private int latestBatteryLevel = GBDevice.BATTERY_UNKNOWN;
    private boolean batteryReceiverRegistered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || !getDevice().getAddress().equalsIgnoreCase(device.getAddress())) {
                return;
            }
            latestBatteryLevel = intent.getIntExtra(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_EXTRA_BATTERY_LEVEL, GBDevice.BATTERY_UNKNOWN);
        }
    };

    public SennheiserMomentumSupport() {
        super(LOG, 1024);
        addSupportedService(SERIAL_PORT_SERVICE);
    }

    @Override
    public void setContext(final GBDevice gbDevice, final BluetoothAdapter btAdapter, final Context context) {
        super.setContext(gbDevice, btAdapter, context);
        if (!batteryReceiverRegistered) {
            ContextCompat.registerReceiver(context, batteryReceiver,
                    new IntentFilter(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_ACTION_BATTERY_LEVEL_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED);
            batteryReceiverRegistered = true;
        }
    }

    @Override
    public void dispose() {
        synchronized (ConnectionMonitor) {
            if (batteryReceiverRegistered) {
                getContext().unregisterReceiver(batteryReceiver);
                batteryReceiverRegistered = false;
            }
            super.dispose();
        }
    }

    /**
     * The headset refuses a connection while it is being paired, and it is added and paired and
     * connected at the same time: adding it from Gadgetbridge starts the bond and the periodic
     * reconnect at once, and the connection attempt makes the pairing fail. Wait for the bond
     * instead - {@link nodomain.freeyourgadget.gadgetbridge.util.BondingUtil} connects once it is
     * bonded.
     */
    boolean isBonded() {
        return getBluetoothAdapter().getRemoteDevice(getDevice().getAddress()).getBondState() == BluetoothDevice.BOND_BONDED;
    }

    @Override
    public boolean connect() {
        if (!isBonded()) {
            LOG.info("Not connecting to the headset while it is not bonded");
            return false;
        }
        return super.connect();
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.write(SennheiserMomentumProtocol.encode(CMD_GET_APPLICATION_VERSION));
        builder.write(SennheiserMomentumProtocol.encode(CMD_GET_LED_CONTROL));
        builder.write(SennheiserMomentumProtocol.encode(CMD_GET_VOICE_PROMPT_CONTROL));
        builder.write(SennheiserMomentumProtocol.encode(CMD_GET_SPEECH_RECOGNITION_CONTROL));
        builder.write(SennheiserMomentumProtocol.encode(CMD_GET_VOLUME_ORIENTATION));
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        builder.run(this::applyLatestBatteryLevel);
        return builder;
    }

    @Override
    public void onSocketRead(final byte[] data) {
        for (final GBDeviceEvent event : protocol.decode(data)) {
            evaluateGBDeviceEvent(event);
        }
    }

    @Override
    public void onSendConfiguration(final String config) {
        final DevicePrefs prefs = getDevicePrefs();
        switch (config) {
            case PREF_SENNHEISER_MOMENTUM_LED:
                sendCommand("set LED", SennheiserMomentumProtocol.encodeSetting(CMD_SET_LED_CONTROL, prefs.getBoolean(config, true)));
                return;
            case PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS:
                sendCommand("set voice prompts", SennheiserMomentumProtocol.encodeSetting(CMD_SET_VOICE_PROMPT_CONTROL, prefs.getBoolean(config, true)));
                return;
            case PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER:
                sendCommand("set voice answer", SennheiserMomentumProtocol.encodeSetting(CMD_SET_SPEECH_RECOGNITION_CONTROL, prefs.getBoolean(config, false)));
                return;
            case PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS:
                sendCommand("set volume orientation", SennheiserMomentumProtocol.encodeSetting(CMD_SET_VOLUME_ORIENTATION, prefs.getBoolean(config, false)));
                return;
            default:
                super.onSendConfiguration(config);
        }
    }

    @Override
    public void onFindDevice(final boolean start) {
        if (start) {
            sendCommand("find device", SennheiserMomentumProtocol.encodeFindDevice());
        }
    }

    @Override
    public void onPowerOff() {
        sendCommand("power off", SennheiserMomentumProtocol.encode(CMD_POWER_OFF));
    }

    void applyLatestBatteryLevel() {
        if (latestBatteryLevel == GBDevice.BATTERY_UNKNOWN) {
            return;
        }
        final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
        batteryInfo.state = BatteryState.BATTERY_NORMAL;
        batteryInfo.level = latestBatteryLevel;
        evaluateGBDeviceEvent(batteryInfo);
    }

    private void sendCommand(final String taskName, final byte[] frame) {
        final TransactionBuilder builder = createTransactionBuilder(taskName);
        builder.write(frame);
        builder.queue();
    }
}
