/*  Copyright (C) 2021-2026 Arjan Schrijver, Daniel Dakhno, Dominic Monroe

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.bose;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointDevice;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.bose.AbstractBoseCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bose.BoseDeviceConfig;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

import static nodomain.freeyourgadget.gadgetbridge.service.devices.bose.BoseProtocol.*;

public class BoseSupport extends AbstractHeadphoneBTBRDeviceSupport {
    public static final Logger LOG = LoggerFactory.getLogger(BoseSupport.class);

    private final BoseFrameParser frameParser = new BoseFrameParser();
    private BoseDeviceConfig deviceConfig;

    private int shortcutButtonId = BUTTON_SHORTCUT;
    private int shortcutEventType = BUTTON_EVENT_PRESS_AND_HOLD;

    private final Map<String, String> pairedDeviceNames = new LinkedHashMap<>();
    private final Map<String, PairedDevice> knownDevices = new LinkedHashMap<>();
    private String activeSourceMac;

    private final BroadcastReceiver a2dpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || !getDevice().getAddress().equalsIgnoreCase(device.getAddress())) {
                return;
            }
            final int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
            if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED) {
                LOG.info("A2DP {} for the headset", state == BluetoothProfile.STATE_CONNECTED ? "connected" : "disconnected");
                refreshPairedDevices();
            }
        }
    };

    private final BroadcastReceiver multipointReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final GBDevice device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
            if (device == null || !getDevice().getAddress().equalsIgnoreCase(device.getAddress())) {
                return;
            }

            final String address = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS);
            switch (intent.getAction()) {
                case MultipointPairingActivity.ACTION_MULTIPOINT_ENABLE:
                    sendMultipointCommand("enable multipoint", setMultipoint(true));
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE:
                    if (GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).getBoolean(
                            DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_DISABLE_SUPPORTED, false)) {
                        sendMultipointCommand("disable multipoint", setMultipoint(false));
                    }
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS:
                    sendMultipointCommand("get multipoint", getMultipoint());
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES:
                    refreshPairedDevices();
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE:
                    if (address != null) {
                        sendMultipointCommand("connect " + address, connectDevice(macToBytes(address)));
                    }
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE:
                    if (address != null) {
                        sendMultipointCommand("disconnect " + address, disconnectDevice(macToBytes(address)));
                    }
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE:
                    if (address != null) {
                        sendMultipointCommand("forget " + address, removeDevice(macToBytes(address)));
                    }
                    break;
                case MultipointPairingActivity.ACTION_MULTIPOINT_START_PAIRING:
                    final boolean enabled = intent.getBooleanExtra(
                            MultipointPairingActivity.EXTRA_PAIRING_ENABLED, false);
                    sendMultipointCommand(enabled ? "enter pairing mode" : "leave pairing mode",
                            setPairingMode(enabled));
                    broadcastMultipointPairing(enabled);
                    break;
                default:
                    break;
            }
        }
    };

    public BoseSupport() {
        super(LOG, 1024);
        addSupportedService(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
    }

    @Override
    public void setContext(@NonNull final GBDevice gbDevice,
                           @NonNull final BluetoothAdapter btAdapter,
                           @NonNull final Context context) {
        super.setContext(gbDevice, btAdapter, context);
        deviceConfig = ((AbstractBoseCoordinator) gbDevice.getDeviceCoordinator()).getDeviceConfig();
        context.registerReceiver(a2dpReceiver,
                new IntentFilter("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"));
        final IntentFilter multipointFilter = new IntentFilter();
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_ENABLE);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE);
        multipointFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_START_PAIRING);
        LocalBroadcastManager.getInstance(context).registerReceiver(multipointReceiver, multipointFilter);
    }

    @Override
    public void dispose() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(multipointReceiver);
        try {
            getContext().unregisterReceiver(a2dpReceiver);
        } catch (final Exception e) {
            LOG.warn("Failed to unregister A2DP receiver", e);
        }
        super.dispose();
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        final byte[] connectPayload = connectHandshake();
        final byte[] notificationPayload = enableNotificationsForFunctionBlocks(BLOCK_PRODUCT_INFO,
                BLOCK_SETTINGS, BLOCK_STATUS, BLOCK_DEVICE_MANAGEMENT, BLOCK_AUDIO_MANAGEMENT);
        final byte[] batteryPayload = getBattery();
        final byte[] firmwarePayload = getFirmwareVersion();
        final byte[] mediaControlCapabilitiesPayload = getMediaControlCapabilities();
        final byte[] multipointPayload = getMultipoint();
        final byte[] voicePromptsPayload = getVoicePrompts();
        final byte[] standbyTimerPayload = getStandbyTimer();
        final byte[] buttonsPayload = getButtons();
        final byte[] listDevicesPayload = listPairedDevices();
        final byte[] sourcePayload = getSourceInfo();
        for (final byte[] payload : new byte[][]{connectPayload, notificationPayload, batteryPayload,
                firmwarePayload, mediaControlCapabilitiesPayload, multipointPayload,
                voicePromptsPayload, standbyTimerPayload, buttonsPayload, listDevicesPayload,
                sourcePayload}) {
            builder.write(payload);
        }
        if (deviceConfig.getCnc() != null) {
            builder.write(getCnc());
        }
        if (deviceConfig.getAnr() != null) {
            builder.write(getAnr());
        }

        builder.setDeviceState(GBDevice.State.INITIALIZED);

        return builder;
    }

    @Override
    public void onSocketRead(final byte[] bytes) {
        LOG.debug("Bose RX: {}", StringUtils.bytesToHex(bytes));

        final List<byte[]> frames = frameParser.feed(bytes);
        for (final byte[] frame : frames) {
            try {
                handleFrame(frame);
            } catch (final Exception e) {
                LOG.error("Failed to handle Bose frame " + StringUtils.bytesToHex(frame), e);
            }
        }
    }

    private void handleFrame(final byte[] frame) {
        final int block = frame[0] & 0xFF;
        final int function = frame[1] & 0xFF;
        final int operator = frame[2] & 0x0F;
        final byte[] payload = Arrays.copyOfRange(frame, 4, frame.length);

        if (operator == OP_ERROR) {
            if (block == BLOCK_SETTINGS && function == FUNCTION_MULTIPOINT) {
                // Treat a multipoint error as enabled but not disableable.
                syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_SUPPORTED, true);
                syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_DISABLE_SUPPORTED, false);
                return;
            }
            LOG.warn("Bose BMAP error on block 0x{} function 0x{}: {}",
                    Integer.toHexString(block), Integer.toHexString(function),
                    StringUtils.bytesToHex(payload));
            return;
        }

        switch (block) {
            case BLOCK_PRODUCT_INFO:
                handleProductInfo(function, operator, payload);
                break;
            case BLOCK_STATUS:
                if (function == FUNCTION_BATTERY && operator == OP_STATUS) {
                    final int level = decodeBatteryLevel(payload);
                    if (level >= 0 && level <= 100) {
                        final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
                        batteryInfo.level = (short) level;
                        batteryInfo.state = BatteryState.BATTERY_NORMAL;
                        evaluateGBDeviceEvent(batteryInfo);
                    } else {
                        LOG.debug("Ignoring implausible battery level: {}", level);
                    }
                }
                break;
            case BLOCK_DEVICE_MANAGEMENT:
                handleDeviceManagement(function, operator, payload);
                break;
            case BLOCK_AUDIO_MANAGEMENT:
                handleAudioManagement(function, operator, payload);
                break;
            case BLOCK_SETTINGS:
                handleSettings(function, operator, payload);
                break;
            default:
                LOG.debug("Ignoring Bose frame from unknown block 0x{}", Integer.toHexString(block));
                break;
        }
    }

    private void handleAudioManagement(final int function, final int operator, final byte[] payload) {
        switch (function) {
            case FUNCTION_SOURCE:
                if (operator == OP_STATUS || operator == OP_RESULT) {
                    final int type = decodeActiveSourceType(payload);
                    final String mac = decodeActiveSourceMac(payload);
                    if (mac != null) {
                        LOG.info("Bose active source: Bluetooth {}", mac);
                        activeSourceMac = mac;
                    } else {
                        final String typeName = type == SOURCE_AUXILIARY ? "auxiliary" : "none";
                        LOG.info("Bose active source: {}", typeName);
                        activeSourceMac = null;
                    }
                    broadcastMultipointList();
                }
                break;
            case FUNCTION_MEDIA_CONTROL:
                LOG.debug("Bose media control response: {}", StringUtils.bytesToHex(payload));
                break;
            default:
                break;
        }
    }

    private void handleDeviceManagement(final int function, final int operator, final byte[] payload) {
        switch (function) {
            case FUNCTION_CONNECT_DEVICE:
                // Result is sent when the connection completes; Processing only acknowledges the start
                if (operator == OP_RESULT) {
                    LOG.info("Bose connect result: {}", StringUtils.bytesToHex(payload));
                    if (payload.length >= 6) {
                        onDeviceConnected(bytesToMac(payload, 0));
                    }
                } else if (operator == OP_PROCESSING) {
                    LOG.debug("Bose connect in progress");
                }
                break;
            case FUNCTION_DISCONNECT_DEVICE:
                if (operator == OP_RESULT) {
                    LOG.info("Bose disconnect result: {}", StringUtils.bytesToHex(payload));
                    if (payload.length >= 6) {
                        onDeviceDisconnected(bytesToMac(payload, 0));
                    }
                } else if (operator == OP_PROCESSING) {
                    LOG.debug("Bose disconnect in progress");
                }
                break;
            case FUNCTION_PAIRING_MODE:
                LOG.info("Bose pairing mode response: {}", StringUtils.bytesToHex(payload));
                break;
            case FUNCTION_REMOVE_DEVICE:
                if (operator == OP_RESULT) {
                    LOG.info("Bose remove device result: {}", StringUtils.bytesToHex(payload));
                    if (payload.length >= 6) {
                        onDeviceRemoved(bytesToMac(payload, 0));
                    }
                } else if (operator == OP_PROCESSING) {
                    LOG.debug("Bose remove device in progress");
                }
                break;
            case FUNCTION_LIST_DEVICES:
                if (operator == OP_STATUS || operator == OP_RESULT) {
                    final List<PairedDevice> devices = decodePairedDevices(payload);
                    LOG.info("Bose paired devices: {}", devices);
                    knownDevices.clear();
                    for (final PairedDevice device : devices) {
                        knownDevices.put(device.mac, device);
                    }
                    broadcastMultipointList();
                    final TransactionBuilder builder = createTransactionBuilder("query device names");
                    boolean anyUnknown = false;
                    for (final PairedDevice device : devices) {
                        if (!pairedDeviceNames.containsKey(device.mac)) {
                            builder.write(getDeviceInfo(macToBytes(device.mac)));
                            anyUnknown = true;
                        }
                    }
                    if (anyUnknown) {
                        builder.queue();
                    }
                }
                break;
            case FUNCTION_DEVICE_INFO:
                if (operator == OP_STATUS) {
                    final String summary = decodeDeviceInfoSummary(payload);
                    if (summary != null) {
                        LOG.info("Bose device info: {}", summary);
                    }
                    final String mac = payload.length >= 6 ? bytesToMac(payload, 0) : null;
                    final String name = decodeDeviceInfoName(payload);
                    if (mac != null && name != null && !name.isEmpty()) {
                        pairedDeviceNames.put(mac, name);
                        if (knownDevices.containsKey(mac)) {
                            broadcastMultipointList();
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private void onDeviceConnected(final String mac) {
        final PairedDevice device = findDevice(mac);
        if (device != null) {
            knownDevices.put(device.mac, new PairedDevice(device.mac, true));
            broadcastMultipointList();
            return;
        }
        knownDevices.put(mac, new PairedDevice(mac, true));
        broadcastMultipointList();
        final TransactionBuilder builder = createTransactionBuilder("query device info");
        builder.write(getDeviceInfo(macToBytes(mac)));
        builder.queue();
    }

    private void onDeviceDisconnected(final String mac) {
        final PairedDevice device = findDevice(mac);
        if (device == null) {
            return;
        }
        knownDevices.put(device.mac, new PairedDevice(device.mac, false));
        broadcastMultipointList();
    }

    private void onDeviceRemoved(final String mac) {
        final PairedDevice device = findDevice(mac);
        if (device == null) {
            return;
        }
        knownDevices.remove(device.mac);
        pairedDeviceNames.remove(device.mac);
        broadcastMultipointList();
    }

    private PairedDevice findDevice(final String mac) {
        for (final PairedDevice device : knownDevices.values()) {
            if (device.mac.equalsIgnoreCase(mac)) {
                return device;
            }
        }
        return null;
    }

    private void refreshPairedDevices() {
        if (!getDevice().isConnected()) {
            return;
        }
        final TransactionBuilder builder = createTransactionBuilder("refresh paired devices");
        builder.write(listPairedDevices());
        builder.write(getSourceInfo());
        builder.queue();
    }

    private void broadcastMultipointList() {
        final List<MultipointDevice> devices = new ArrayList<>();
        for (final PairedDevice device : knownDevices.values()) {
            devices.add(new MultipointDevice(
                    device.mac,
                    pairedDeviceNames.get(device.mac),
                    device.connected,
                    device.mac.equalsIgnoreCase(activeSourceMac),
                    true
            ));
        }
        devices.sort((a, b) -> {
            final String nameA = a.getName() != null ? a.getName() : a.getAddress();
            final String nameB = b.getName() != null ? b.getName() : b.getAddress();
            final int result = StringUtils.naturalCompare(nameA, nameB);
            return result != 0 ? result : a.getAddress().compareToIgnoreCase(b.getAddress());
        });

        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_DEVICE_LIST);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putParcelableArrayListExtra(MultipointPairingActivity.EXTRA_DEVICE_LIST,
                new ArrayList<>(devices));
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private void sendMultipointCommand(final String name, final byte[] command) {
        final TransactionBuilder builder = createTransactionBuilder(name);
        builder.write(command);
        builder.queue();
    }

    private void broadcastMultipointPairing(final boolean enabled) {
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_PAIRING_UPDATE);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putExtra(MultipointPairingActivity.EXTRA_PAIRING_ENABLED, enabled);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private void handleProductInfo(final int function, final int operator, final byte[] payload) {
        switch (function) {
            case FUNCTION_INIT_HANDSHAKE:
                LOG.debug("Bose init handshake response: {}", StringUtils.bytesToHex(payload));
                break;
            case FUNCTION_FIRMWARE_VERSION:
                if (operator == OP_STATUS && payload.length > 0) {
                    final String version = new String(payload, StandardCharsets.UTF_8).trim();
                    LOG.info("Bose firmware version: {}", version);
                    if (!version.isEmpty()) {
                        final GBDeviceEventVersionInfo versionInfo = new GBDeviceEventVersionInfo();
                        versionInfo.fwVersion = version;
                        evaluateGBDeviceEvent(versionInfo);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void handleSettings(final int function, final int operator, final byte[] payload) {
        if (operator != OP_STATUS && operator != OP_RESULT) {
            return;
        }
        switch (function) {
            case FUNCTION_NOISE_CANCELLING:
                final int cncLevel = decodeCncLevel(payload);
                if (cncLevel >= 0) {
                    LOG.debug("Bose noise cancelling status: level={}", cncLevel);
                    syncIntPref(DeviceSettingsPreferenceConst.PREF_BOSE_CNC_LEVEL, cncLevel);
                }
                break;
            case FUNCTION_ANR:
                final int anrLevel = decodeAnrLevel(payload);
                if (anrLevel >= 0) {
                    LOG.debug("Bose noise cancelling status: level={}", anrLevel);
                    syncIntPref(DeviceSettingsPreferenceConst.PREF_BOSE_ANR_LEVEL, anrLevel);
                }
                break;
            case FUNCTION_MULTIPOINT:
                final Boolean multipoint = decodeMultipointEnabled(payload);
                final Boolean multipointSupported = decodeMultipointSupported(payload);
                final Boolean multipointDisableSupported = decodeMultipointDisableSupported(payload);
                if (multipoint != null && multipointSupported != null && multipointDisableSupported != null) {
                    LOG.debug("Bose multipoint: enabled={} supported={} disableSupported={}",
                            multipoint, multipointSupported, multipointDisableSupported);
                    syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_SUPPORTED,
                            multipointSupported);
                    syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_DISABLE_SUPPORTED,
                            multipointDisableSupported);
                    broadcastMultipointStatus(multipoint);
                }
                break;
            case FUNCTION_VOICE_PROMPTS:
                final Boolean promptsEnabled = decodeVoicePromptsEnabled(payload);
                final int language = decodeVoicePromptsLanguage(payload);
                final Boolean promptsTogglable = decodeVoicePromptsTogglable(payload);
                if (promptsEnabled != null) {
                    syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS, promptsEnabled);
                }
                if (language >= 0) {
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_LANGUAGE,
                            String.valueOf(language));
                }
                if (promptsTogglable != null) {
                    syncBooleanPref(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_TOGGLABLE,
                            promptsTogglable);
                }
                final int supportedMask = decodeVoicePromptsSupportedMask(payload);
                if (supportedMask >= 0) {
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_SUPPORTED,
                            String.valueOf(supportedMask));
                }
                break;
            case FUNCTION_STANDBY_TIMER:
                final int minutes = decodeStandbyTimerMinutes(payload);
                if (minutes >= 0) {
                    LOG.debug("Bose standby timer: {} minutes", minutes);
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_AUTO_OFF,
                            String.valueOf(minutes));
                }
                break;
            case FUNCTION_BUTTONS:
                final ButtonConfig buttonConfig = decodeButtonConfig(payload);
                if (buttonConfig != null) {
                    LOG.info("Bose button config: button=0x{} event={} mode={} supported=0x{} unavailable=0x{}",
                            Integer.toHexString(buttonConfig.buttonId), buttonConfig.eventType, buttonConfig.currentMode,
                            Integer.toHexString(buttonConfig.supportedMask), Integer.toHexString(buttonConfig.unavailableMask));
                    shortcutButtonId = buttonConfig.buttonId;
                    shortcutEventType = buttonConfig.eventType;
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT,
                            String.valueOf(buttonConfig.currentMode));
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_SUPPORTED,
                            String.valueOf(buttonConfig.supportedMask));
                    syncStringPref(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_UNAVAILABLE,
                            String.valueOf(buttonConfig.unavailableMask));
                }
                break;
            default:
                break;
        }
    }

    private void syncIntPref(final String key, final int value) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (prefs.getInt(key, Integer.MIN_VALUE) != value) {
            LOG.info("Syncing pref {} to {} from device", key, value);
            prefs.edit().putInt(key, value).apply();
        }
    }

    private void syncBooleanPref(final String key, final boolean value) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (prefs.getBoolean(key, true) != value) {
            LOG.info("Syncing pref {} to {} from device", key, value);
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    private void syncStringPref(final String key, final String value) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (!value.equals(prefs.getString(key, null))) {
            LOG.info("Syncing pref {} to {} from device", key, value);
            prefs.edit().putString(key, value).apply();
        }
    }

    private static int parsePrefInt(final String value, final int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private void broadcastMultipointStatus(final boolean enabled) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_STATUS_UPDATE);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_ENABLED, enabled);
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_DISABLE_SUPPORTED,
                prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_BOSE_MULTIPOINT_DISABLE_SUPPORTED, false));
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    @Override
    public void onSendConfiguration(@NonNull final String config) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());

        if (DeviceSettingsPreferenceConst.PREF_BOSE_CNC_LEVEL.equals(config)) {
            final TransactionBuilder builder = createTransactionBuilder("set CNC level");
            builder.write(encodeCnc());
            builder.queue();
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_ANR_LEVEL.equals(config)) {
            final TransactionBuilder builder = createTransactionBuilder("set ANR level");
            builder.write(encodeAnr());
            builder.queue();
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS.equals(config)
                || DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_LANGUAGE.equals(config)) {
            final boolean enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS, true);
            final int language = parsePrefInt(prefs.getString(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_LANGUAGE, "1"), 1);
            final TransactionBuilder builder = createTransactionBuilder("set voice prompts");
            builder.write(setVoicePrompts(enabled, language));
            builder.queue();
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_AUTO_OFF.equals(config)) {
            final int minutes = parsePrefInt(prefs.getString(DeviceSettingsPreferenceConst.PREF_BOSE_AUTO_OFF, "60"), 60);
            if (!deviceConfig.getStandbyTimerDurations().contains(minutes)) {
                LOG.warn("Ignoring unsupported standby timer duration: {}", minutes);
                return;
            }
            final TransactionBuilder builder = createTransactionBuilder("set auto-off");
            builder.write(setStandbyTimer(minutes));
            builder.queue();
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT.equals(config)) {
            final int mode = parsePrefInt(prefs.getString(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT, "-1"), -1);
            if (mode >= 0 && prefs.getString(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_SUPPORTED, null) != null) {
                final TransactionBuilder builder = createTransactionBuilder("set shortcut action");
                builder.write(setActionButton(shortcutButtonId, shortcutEventType, mode));
                builder.queue();
            }
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PLAY.equals(config)) {
            sendMediaControl(MEDIA_PLAY);
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PAUSE.equals(config)) {
            sendMediaControl(MEDIA_PAUSE);
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_NEXT.equals(config)) {
            sendMediaControl(MEDIA_NEXT);
        } else if (DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PREVIOUS.equals(config)) {
            sendMediaControl(MEDIA_PREVIOUS);
        }
    }

    private void sendMediaControl(final int action) {
        final TransactionBuilder builder = createTransactionBuilder("media control 0x"
                + String.format("%02x", action));
        builder.write(mediaControl(action));
        builder.queue();
    }

    @NonNull
    private byte[] encodeCnc() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final int level = prefs.getInt(DeviceSettingsPreferenceConst.PREF_BOSE_CNC_LEVEL,
                deviceConfig.getCnc().getDefaultValue());
        return setCnc(level);
    }

    @NonNull
    private byte[] encodeAnr() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final int level = prefs.getInt(DeviceSettingsPreferenceConst.PREF_BOSE_ANR_LEVEL,
                deviceConfig.getAnr().getDefaultValue());
        return setAnr(level);
    }
}
