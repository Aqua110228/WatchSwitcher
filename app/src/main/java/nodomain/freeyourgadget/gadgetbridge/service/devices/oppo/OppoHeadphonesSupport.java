/*  Copyright (C) 2024 José Rebelo
    Copyright (C) 2026 NTeditor

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.Set;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.util.LEB128Utils;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoCommand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigSide;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigValue;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.MiscConfigType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.AncConfigType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.AncConfigValue;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.SubscriptionType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.MultipointDeviceAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules.FirmwareVersionModule;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules.MiscConfigModule;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules.MultipointDevicesModule;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoHeadphonesCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoHeadphonesPreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;

public class OppoHeadphonesSupport extends AbstractHeadphoneBTBRDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(OppoHeadphonesSupport.class);
    private static final int MAX_MTU = 2048;
    public static final byte CMD_PREAMBLE = (byte) 0xAA;
    public static final short CMD_MASK_RESPONSE = (short) 0x8000;

    private final ByteBuffer packetBuffer = ByteBuffer.allocate(MAX_MTU).order(ByteOrder.LITTLE_ENDIAN);
    private int seqNum = 0;

    private final Queue<OppoMessage> messageQueue = new LinkedList<>();
    private OppoMessage pendingMessage = null;
    private int timeoutRetries = 0;
    private final Handler timeoutHandler = new Handler();

    public OppoHeadphonesSupport() {
        super(LOG, MAX_MTU);
    }

    @Override
    public UUID getSupportedService() {
        return getCoordinator().ctrlUuid(getDevice()).getUuid();
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public void setContext(@NonNull GBDevice gbDevice, @NonNull BluetoothAdapter btAdapter, @NonNull Context context) {
        super.setContext(gbDevice, btAdapter, context);
        if (getCoordinator().supportsMultipoint(getDevice())) {
            multipointReceiverSetup();
        }
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        packetBuffer.clear();
        timeoutHandler.removeCallbacksAndMessages(null);
        messageQueue.clear();
        pendingMessage = null;
        timeoutRetries = 0;
        seqNum = 0;

        batteryReq();
        queueCommand(getMiscConfigModule().encodeReq(getMiscSupports()));
        ancConfigReq();
        touchConfigReq();
        subscriptionSet();
        queueCommand(getFwVersionModule().encodeReq());

        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }

    private EnumSet<MiscConfigType> getMiscSupports() {
        final EnumSet<MiscConfigType> supports = EnumSet.noneOf(MiscConfigType.class);
        if (getCoordinator().supportsLdac(getDevice()))
            supports.add(MiscConfigType.LDAC);
        if (getCoordinator().supportsMultipoint(getDevice()))
            supports.add(MiscConfigType.MULTIPOINT);
        if (getCoordinator().supportsGameMode(getDevice()))
            supports.add(MiscConfigType.GAME_MODE);
        if (getCoordinator().supportsFindPhone(getDevice()))
            supports.add(MiscConfigType.FIND_PHONE);
        return supports;
    }

    @Override
    public void dispose() {
        synchronized (ConnectionMonitor) {
            timeoutHandler.removeCallbacksAndMessages(null);
            if (getCoordinator().supportsMultipoint(getDevice())) {
                LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(multipointReceiver);
            }
            super.dispose();
        }
    }

    @Override
    public void onSocketRead(final byte[] data) {
        packetBuffer.put(data);
        packetBuffer.flip();

        while (packetBuffer.hasRemaining()) {
            packetBuffer.mark();

            if (packetBuffer.remaining() < 2) {
                packetBuffer.reset();
                break;
            }

            final byte preamble = packetBuffer.get();
            if (preamble != CMD_PREAMBLE) {
                LOG.warn("Unexpected preamble {}, skipping 1 byte", preamble);
                continue;
            }

            int totalLength;
            try {
                totalLength = (int) LEB128Utils.decodeUnsigned(packetBuffer);
            } catch (BufferUnderflowException e) {
                packetBuffer.reset();
                break;
            }
            if (packetBuffer.remaining() < totalLength) {
                LOG.info("Got partial response with {} bytes, expected {}",
                        packetBuffer.remaining(), totalLength);
                packetBuffer.reset();
                break;
            }

            final byte[] packet = new byte[totalLength];
            packetBuffer.get(packet);
            handlePacket(packet);
        }

        packetBuffer.compact();
    }

    protected void handlePacket(final byte[] packet) {
        final ByteBuffer buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        final short zero = buf.getShort();
        if (zero != 0 && zero != 4) {
            // 0 on oppo, 4 on realme?
            // 8 on realme buds t200?
            LOG.warn("Unexpected bytes: {}, expected 0 or 4", zero);
        }

        final short code = buf.getShort();
        final OppoCommand command = OppoCommand.fromCode(code);
        if (command == null) {
            LOG.warn("Unknown command code 0x{}", OppoUtils.numberToHex(code, 4));
            return;
        }

        final int seq = buf.get();
        final int payloadLength = buf.getShort() & 0xFFFF;
        final int expectedPayloadLength = buf.capacity() - 7;
        if (payloadLength != expectedPayloadLength) {
            LOG.error("Unexpected payload length: {}, expected {}", payloadLength, expectedPayloadLength);
            return;
        }

        final byte[] payload = new byte[payloadLength];
        buf.get(payload);

        boolean sendNext;
        try {
            handleCommand(command, payload);
            if (pendingMessage != null) {
                sendNext = (pendingMessage.command().getCode() | CMD_MASK_RESPONSE) == command.getCode();
            } else {
                sendNext = false;
            }
        } catch (Exception e) {
            LOG.error("Failed to handle command", e);
            sendNext = true;
        }

        if (sendNext) {
            sendNextCommand();
        }
    }

    @Override
    public void onSendConfiguration(String config) {
        if (config.startsWith(OppoHeadphonesPreferences.TOUCH_PREFIX)) {
            touchConfigSet(config);
            return;
        }

        switch (config) {
            case OppoHeadphonesPreferences.LDAC -> {
                final boolean isEnabled = getDevicePrefs().getBoolean(OppoHeadphonesPreferences.LDAC, false);
                queueCommand(getMiscConfigModule().encodeSet(MiscConfigType.LDAC, isEnabled));
            }
            case OppoHeadphonesPreferences.GAME_MODE -> {
                final boolean isEnabled = getDevicePrefs().getBoolean(OppoHeadphonesPreferences.GAME_MODE, false);
                queueCommand(getMiscConfigModule().encodeSet(MiscConfigType.GAME_MODE, isEnabled));
            }
            case OppoHeadphonesPreferences.FIND_PHONE -> {
                final boolean isEnabled = getDevicePrefs().getBoolean(OppoHeadphonesPreferences.FIND_PHONE, false);
                queueCommand(getMiscConfigModule().encodeSet(MiscConfigType.FIND_PHONE, isEnabled));
            }
            case OppoHeadphonesPreferences.ANC_SELECTOR -> ancModeSet();
            case OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES -> touchAncCycleModesSet();
            default -> super.onSendConfiguration(config);
        }
    }

    protected void handleCommand(OppoCommand command, byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload);
        switch (command) {
            case SUBSCRIPTION_ACK, TOUCH_CONFIG_ACK, MISC_CONFIG_ACK, ANC_CONFIG_ACK, FIND_DEVICE_ACK,
                    MULTIPOINT_DEVICES_ACK -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                LOG.debug("Got {}", command);
            }
            case BATTERY_RET -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                parseBattery(payload);
            }
            case SUBSCRIPTION_RET -> {
                parseSubscription(payload);
            }
            case FIRMWARE_RET -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                final GBDeviceEvent event = getFwVersionModule().decodeRet(payload);
                evaluateGBDeviceEvent(event);
            }
            case TOUCH_CONFIG_RET -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                parseTouchConfig(payload);
            }
            case MISC_CONFIG_RET -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                getMiscConfigModule()
                        .decodeRet(payload)
                        .forEach((type, value) -> {
                            switch (type) {
                                case LDAC -> {
                                    evaluateGBDeviceEvent(
                                            new GBDeviceEventUpdatePreferences(
                                                    OppoHeadphonesPreferences.LDAC, value));
                                }
                                case MULTIPOINT -> {
                                    multipointReceiverStatus(value);
                                }
                                case GAME_MODE -> {
                                    evaluateGBDeviceEvent(
                                            new GBDeviceEventUpdatePreferences(
                                                    OppoHeadphonesPreferences.GAME_MODE, value));
                                }
                                case FIND_PHONE -> {
                                    evaluateGBDeviceEvent(
                                            new GBDeviceEventUpdatePreferences(
                                                    OppoHeadphonesPreferences.FIND_PHONE, value));
                                }
                            }
                        });
            }
            case ANC_CONFIG_RET -> {
                final int zero = buf.get();
                if (zero != 0) {
                    LOG.warn("Unexpected non-zero byte 0x{} for {}", OppoUtils.numberToHex(zero, 2), command);
                    break;
                }

                parseAncConfig(payload);
            }
            case FIND_PHONE -> {
                LOG.debug("Got {}", command);
                parseFindPhone(payload);
            }
            case MULTIPOINT_DEVICES_RET -> {
                multipointReceiverDevices(getMultipointDevsModule().decodeRet(payload));
            }
            default -> LOG.warn("Unhandled command {}", command);
        }

    }

    private void batteryReq() {
        queueCommand(OppoCommand.BATTERY_REQ, new byte[0]);
    }

    private void parseBattery(final byte[] payload) {
        final List<GBDeviceEventBatteryInfo> events = new ArrayList<>();
        final int numBatteries = payload[1] & 0xff;
        for (int i = 2; i < payload.length; i += 2) {
            if ((payload[i] & 0xff) == 0xff) {
                continue;
            }
            final int batteryIndex = payload[i] - 1;
            if (batteryIndex < 0 || batteryIndex > 2) {
                LOG.error("Unknown battery index {}", payload[i]);
                break;
            }

            final int batteryLevel = payload[i + 1] & 0x7f;
            if (batteryIndex == 2 && batteryLevel == 0) {
                continue;
            }
            final BatteryState batteryState = (payload[i + 1] & 0x80) != 0 ? BatteryState.BATTERY_CHARGING
                    : BatteryState.BATTERY_NORMAL;

            LOG.debug("Got battery {}: {}%, {}", batteryIndex, batteryLevel, batteryState);

            final GBDeviceEventBatteryInfo eventBatteryInfo = new GBDeviceEventBatteryInfo();
            eventBatteryInfo.batteryIndex = batteryIndex;
            eventBatteryInfo.level = batteryLevel;
            eventBatteryInfo.state = batteryState;
            events.add(eventBatteryInfo);
        }

        List<Integer> processedBatteries = events.stream()
                .map(event -> event.batteryIndex)
                .toList();

        for (int i = 0; i < 3; i++) {
            if (processedBatteries.contains(i)) {
                continue;
            }

            final GBDeviceEventBatteryInfo eventBatteryInfo = new GBDeviceEventBatteryInfo();
            eventBatteryInfo.batteryIndex = i;
            eventBatteryInfo.level = -1;
            eventBatteryInfo.state = BatteryState.UNKNOWN;
            events.add(eventBatteryInfo);
        }

        for (GBDeviceEventBatteryInfo event : events) {
            evaluateGBDeviceEvent(event);
        }
    }

    private void subscriptionSet() {
        final List<SubscriptionType> types = new ArrayList<>();
        types.add(SubscriptionType.BATTERY);
        if (getCoordinator().supportsAnc(getDevice()))
            types.add(SubscriptionType.ANC_SELECTOR);
        if (getCoordinator().supportsGameMode(getDevice()))
            types.add(SubscriptionType.GAME_MODE);
        if (getCoordinator().supportsMultipoint(getDevice()))
            types.add(SubscriptionType.MULTIPOINT);

        final ByteBuffer buf = ByteBuffer.allocate(1 + types.size());
        buf.put((byte) 0x09);
        for (SubscriptionType type : types) {
            buf.put((byte) type.getCode());
        }
        queueCommand(OppoCommand.SUBSCRIPTION_SET, buf.array());
    }

    private void parseSubscription(final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload);
        if (buf.remaining() < 1) {
            LOG.warn("Unexpected payload remaining: {}, expected >=1", buf.remaining());
            return;
        }

        final int typeCode = buf.get() & 0xFF;
        final SubscriptionType type = SubscriptionType.fromCode(typeCode);
        if (type == null) {
            LOG.warn("Unknown subcription type 0x{}", OppoUtils.numberToHex(typeCode, 2));
            return;
        }

        switch (type) {
            case BATTERY: {
                parseBattery(buf.array());
                break;
            }
            case STATUS: {
                LOG.debug("Got status");
                // TODO handle
                break;
            }
            case GAME_MODE: {
                if (buf.remaining() != 1) {
                    LOG.warn("Unexpected payload remaining: {}, expected 1", buf.remaining());
                    return;
                }
                final boolean isEnabled = ((buf.get() & 0xFF) == 0x01);
                LOG.debug("Got misc config for GAME_MODE = {}", isEnabled);
                evaluateGBDeviceEvent(new GBDeviceEventUpdatePreferences(
                        OppoHeadphonesPreferences.GAME_MODE,
                        isEnabled));
                break;
            }
            case ANC_SELECTOR: {
                if (buf.remaining() != 2) {
                    LOG.warn("Unexpected payload remaining: {}, expected 2", buf.remaining());
                    return;
                }

                final int one = buf.get();
                if (one != 1) {
                    LOG.warn("Unexpected payload: {}", StringUtils.bytesToHex(buf.array()));
                }

                final int valueCode = buf.get();
                final AncConfigValue value = AncConfigValue.fromCode(valueCode);
                if (value == null) {
                    LOG.warn("Unknown anc value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }
                LOG.debug("Got anc config for MODE = {}", value);
                evaluateGBDeviceEvent(new GBDeviceEventUpdatePreferences(
                        OppoHeadphonesPreferences.ANC_SELECTOR,
                        value.getPrefId()));
                break;
            }
            case MULTIPOINT: {
                multipointReceiverDevices(getMultipointDevsModule().decodeRet(payload));
                break;
            }
            default: {
                LOG.warn("Unhandled subscription type {}", type);
                break;
            }
        }
    }

    private void touchConfigSet(final String config) {
        final String[] parts = config.split("__");
        final TouchConfigSide side = TouchConfigSide.valueOf(parts[1].toUpperCase(Locale.ROOT));
        final TouchConfigType type = TouchConfigType.valueOf(parts[2].toUpperCase(Locale.ROOT));
        final String valueCode = getDevicePrefs().getString(OppoHeadphonesPreferences.getTouchKey(side, type), null);
        if (valueCode == null) {
            LOG.warn("Failed to get touch option value for {}/{}", side, type);
            return;
        }
        final TouchConfigValue value = TouchConfigValue.valueOf(valueCode.toUpperCase(Locale.ROOT));

        final ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x01);
        buf.put((byte) side.getCode());
        buf.putShort((short) type.getCode());
        buf.put((byte) value.getCode());

        LOG.debug("Send {} {} = {}", side, type, value);
        queueCommand(OppoCommand.TOUCH_CONFIG_SET, buf.array());
    }

    private void touchConfigReq() {
        queueCommand(OppoCommand.TOUCH_CONFIG_REQ, new byte[] { 0x02, 0x03, 0x01 });
    }

    private void parseTouchConfig(final byte[] payload) {
        if ((payload.length - 2) % 4 != 0) {
            LOG.warn("Unexpected touch config ret payload size {}", payload.length);
            return;
        }

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences();
        for (int i = 2; i < payload.length; i += 4) {
            final int sideCode = payload[i] & 0xff;
            final int typeCode = BLETypeConversions.toUint16(payload, i + 1);
            final int valueCode = payload[i + 3] & 0xff;
            final TouchConfigSide side = TouchConfigSide.fromCode(sideCode);
            final TouchConfigType type = TouchConfigType.fromCode(typeCode);
            final TouchConfigValue value = TouchConfigValue.fromCode(valueCode);

            if (side == null) {
                LOG.warn("Unknown touch side code 0x{}", OppoUtils.numberToHex(sideCode, 2));
                continue;
            }
            if (type == null) {
                LOG.warn("Unknown touch type code 0x{}", OppoUtils.numberToHex(typeCode, 4));
                continue;
            }
            if (value == null) {
                LOG.warn("Unknown touch value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                continue;
            }

            LOG.debug("Got touch config for {} {} = {}", side, type, value);

            eventUpdatePreferences.withPreference(
                    OppoHeadphonesPreferences.getTouchKey(side, type),
                    value.name().toLowerCase(Locale.ROOT));
        }
        evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void ancModeSet() {
        final String valuePrefId = getDevicePrefs().getString(
                OppoHeadphonesPreferences.ANC_SELECTOR,
                AncConfigValue.OFF.getPrefId());
        AncConfigValue value = AncConfigValue.fromPrefId(valuePrefId);
        if (value == null) {
            LOG.warn("Unknown ANC prefId = \"{}\"", valuePrefId);
            return;
        }
        LOG.debug("Send AncConfigType.MODE = {}", value);
        ancConfigSet(AncConfigType.MODE, value.getCode());
    }

    private void touchAncCycleModesSet() {
        final Set<String> valuePrefIds = getDevicePrefs().getStringSet(
                OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES,
                Set.of(AncConfigValue.ON.getPrefId(), AncConfigValue.TRANSPARENCY.getPrefId()));
        final EnumSet<AncConfigValue> values = AncConfigValue.fromPrefIds(valuePrefIds);
        if (values.size() < 2) {
            LOG.warn("ANC cycle must contain at least 2 values. Current selection: {}", values);
            return;
        }

        LOG.debug("Send AncConfigType.ANC_TOUCH_CYCLE_MODES = {}", values);
        final int mask = AncConfigValue.toMask(values);
        ancConfigSet(AncConfigType.TOUCH_CYCLE_MODES, mask);
    }

    private void ancConfigSet(final AncConfigType type, final int value) {
        final byte[] payload = new byte[] {
                (byte) type.getCode(),
                (byte) 0x01,
                (byte) value
        };
        queueCommand(OppoCommand.ANC_CONFIG_SET, payload);
    }

    private void ancConfigReq() {
        Consumer<AncConfigType> sendAncConfig = (configType) -> {
            byte[] payload = new byte[] {
                    (byte) configType.getCode(),
                    (byte) 0x01,
            };
            queueCommand(OppoCommand.ANC_CONFIG_REQ, payload);
        };

        if (getCoordinator().supportsAnc(getDevice())) {
            sendAncConfig.accept(AncConfigType.MODE);
            sendAncConfig.accept(AncConfigType.TOUCH_CYCLE_MODES);
        }
    }

    private void parseAncConfig(final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload);
        if (buf.remaining() != 4) {
            LOG.warn("Unexpected anc config ret payload remaining {}, expected 4", buf.remaining());
            return;
        }

        final GBDeviceEventUpdatePreferences event = new GBDeviceEventUpdatePreferences();
        final int zero = buf.get();
        final int typeCode = buf.get() & 0xFF;
        final int one = buf.get();
        final int valueCode = buf.get() & 0xff;

        final AncConfigType type = AncConfigType.fromCode(typeCode);
        if (type == null) {
            LOG.warn("Unknown anc type code 0x{}", OppoUtils.numberToHex(typeCode, 2));
            return;
        }

        switch (type) {
            case MODE: {
                final AncConfigValue value = AncConfigValue.fromCode(valueCode);
                if (value == null) {
                    LOG.warn("Unknown anc value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }

                LOG.debug("Got anc config for {} = {}", type, value);
                event.withPreference(OppoHeadphonesPreferences.ANC_SELECTOR, value.getPrefId());
                break;
            }
            case TOUCH_CYCLE_MODES: {
                final EnumSet<AncConfigValue> values = AncConfigValue.fromMask(valueCode);
                if (values.isEmpty()) {
                    LOG.warn("Unknown anc value mask 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }
                final Set<String> valuePrefIds = AncConfigValue.toPrefIds(values);
                LOG.debug("Got anc config for {} = {}", type, valuePrefIds);
                event.withPreference(OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES, valuePrefIds);
                break;
            }
            default: {
                LOG.debug("Unknown anc type code {}", typeCode);
                break;
            }
        }
        evaluateGBDeviceEvent(event);
    }

    private void parseFindPhone(final byte[] payload) {
        final GBDeviceEventFindPhone event = new GBDeviceEventFindPhone();
        final int eventCode = payload[0];
        if (eventCode == 0x05) {
            event.event = GBDeviceEventFindPhone.Event.START;
        } else if (eventCode == 0x06) {
            event.event = GBDeviceEventFindPhone.Event.STOP;
        } else {
            LOG.warn("Unexpected byte 0x{} for FIND_PHONE", OppoUtils.numberToHex(eventCode, 2));
        }

        evaluateGBDeviceEvent(event);
    }

    @Override
    public void onFindDevice(boolean start) {
        queueCommand(OppoCommand.FIND_DEVICE_REQ, new byte[] { (byte) (start ? 0x01 : 0x00) });
    }

    private final BroadcastReceiver multipointReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            GBDevice device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
            if (device == null || !device.getAddress().equals(gbDevice.getAddress())) {
                return;
            }

            String action = intent.getAction();
            if (action == null) {
                return;
            }

            switch (action) {
                case MultipointPairingActivity.ACTION_MULTIPOINT_ENABLE -> {
                    queueCommand(getMiscConfigModule().encodeSet(MiscConfigType.MULTIPOINT, true));
                    queueCommand(getMiscConfigModule().encodeReq(MiscConfigType.MULTIPOINT));
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE -> {
                    queueCommand(getMiscConfigModule().encodeSet(MiscConfigType.MULTIPOINT, false));
                    queueCommand(getMiscConfigModule().encodeReq(MiscConfigType.MULTIPOINT));
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS -> {
                    queueCommand(getMiscConfigModule().encodeReq(MiscConfigType.MULTIPOINT));
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES -> {
                    queueCommand(getMultipointDevsModule().encodeReq());
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE -> {
                    final String macAddress = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS);
                    queueCommand(
                            getMultipointDevsModule().encodeDeviceAction(macAddress, MultipointDeviceAction.CONNECT));
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE -> {
                    final String macAddress = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS);
                    queueCommand(
                            getMultipointDevsModule().encodeDeviceAction(macAddress, MultipointDeviceAction.DISCONNECT));
                }
                case MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE -> {
                    final String macAddress = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS);
                    queueCommand(
                            getMultipointDevsModule().encodeDeviceAction(macAddress, MultipointDeviceAction.FORGET));
                }
            }
        }
    };

    private void multipointReceiverSetup() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_ENABLE);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE);
        intentFilter.addAction(MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE);

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(multipointReceiver, intentFilter);
    }

    private void multipointReceiverStatus(boolean isEnabled) {
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_STATUS_UPDATE);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_ENABLED, isEnabled);
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_DISABLE_SUPPORTED, true);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private void multipointReceiverDevices(List<MultipointDevice> devices) {
        Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_DEVICE_LIST);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putParcelableArrayListExtra(
                MultipointPairingActivity.EXTRA_DEVICE_LIST,
                new ArrayList<>(devices));
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private void queueCommand(final OppoCommand command, final byte[] payload) {
        queueCommand(new OppoMessage(command, payload));
    }

    private void queueCommand(final OppoMessage message) {
        if (message != null) {
            messageQueue.add(message);

            if (pendingMessage == null) {
                sendNextCommand();
            }
        }
    }

    private void onCommandTimeout() {
        if (timeoutRetries++ < 3) {
            LOG.warn("Timed out waiting for response, retrying attempt {}", timeoutRetries);
            if (pendingMessage != null) {
                sendMessage(pendingMessage);
                return;
            }
        }
        LOG.warn("Timed out waiting for response, giving up");
        sendNextCommand();
    }

    private void sendNextCommand() {
        timeoutHandler.removeCallbacksAndMessages(null);
        timeoutRetries = 0;

        pendingMessage = messageQueue.poll();
        if (pendingMessage != null) {
            LOG.debug("Sending next command in queue: {}", pendingMessage.command());
            sendMessage(pendingMessage);
            return;
        }
        LOG.debug("No more commands in the queue");
    }

    private void sendMessage(OppoMessage message) {
        final TransactionBuilder builder = createTransactionBuilder(message.command().name().toLowerCase());
        builder.write(encodeCommand(message.command(), message.payload()));
        builder.queue();
        timeoutHandler.postDelayed(() -> onCommandTimeout(), 2000L);
    }

    byte[] encodeCommand(final OppoCommand command, final byte[] payload) {
        final int totalLength = 7 + payload.length;
        final byte[] totalLengthBytes = LEB128Utils.encodeUnsigned((long) totalLength);
        final ByteBuffer buf = ByteBuffer.allocate(1 + totalLengthBytes.length + totalLength)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.put(CMD_PREAMBLE);
        buf.put(totalLengthBytes);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.putShort(command.getCode());
        buf.put((byte) (seqNum++ & 0xff));
        buf.putShort((short) payload.length);
        buf.put(payload);
        return buf.array();
    }

    protected FirmwareVersionModule getFwVersionModule() {
        return new FirmwareVersionModule(getContext());
    }

    protected MiscConfigModule getMiscConfigModule() {
        return new MiscConfigModule(getContext());
    }

    protected MultipointDevicesModule getMultipointDevsModule() {
        return new MultipointDevicesModule(getContext(), getCoordinator().multipointMacOrder(getDevice()));
    }

    @Override
    protected OppoHeadphonesCoordinator getCoordinator() {
        return (OppoHeadphonesCoordinator) getDevice().getDeviceCoordinator();
    }
}
