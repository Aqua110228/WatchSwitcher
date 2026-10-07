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
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.ZeblazeConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.proto.zeblaze.ZeblazeProto;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;

/**
 * Support for the Zeblaze Beyond 3 Pro's own "ZH_SDK" GATT protocol.
 *
 * The protocol is unencrypted and unauthenticated (no BLE pairing/bonding,
 * no application-layer crypto): every message, in either direction, goes
 * through the same four-frame handshake on one of two characteristics
 * ({@link ZeblazeConstants#UUID_CHARACTERISTIC_COMMAND_WRITE} for app-to-watch
 * commands, {@link ZeblazeConstants#UUID_CHARACTERISTIC_COMMAND_READ} for the
 * watch's responses) -- a 6-byte header frame announcing a chunk count, a
 * 6-byte ready-ack, N data chunks, then a 6-byte complete-ack. The reassembled
 * payload is a protobuf message, see {@code zeblaze.proto}.
 *
 * This initial implementation only performs GET_DEVICE_INFO, which is enough
 * to report firmware version and battery level -- activity/workout sync is
 * not implemented yet.
 */
public class Beyond3ProSupport extends AbstractBTLESingleDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(Beyond3ProSupport.class);

    private static final byte[] HEADER_PREFIX = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] ACK_READY = {0x00, 0x00, 0x01, 0x01, 0x00, 0x00};
    private static final byte[] ACK_COMPLETE = {0x00, 0x00, 0x01, 0x00, 0x00, 0x00};

    private enum SendState {
        IDLE, AWAITING_READY_ACK, AWAITING_COMPLETE_ACK
    }

    private BluetoothGattCharacteristic commandReadCharacteristic;
    private BluetoothGattCharacteristic commandWriteCharacteristic;

    private SendState sendState = SendState.IDLE;
    private byte[] pendingOutgoingPayload;

    private boolean awaitingResponseChunks = false;
    private int expectedChunkCount;
    private final Map<Integer, byte[]> receivedChunks = new TreeMap<>();

    public Beyond3ProSupport() {
        super(LOG);
        addSupportedService(ZeblazeConstants.UUID_SERVICE_ZH_SDK);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        // A support instance may outlive a connection. Do not carry a partially
        // completed request or response into the next initialization.
        sendState = SendState.IDLE;
        pendingOutgoingPayload = null;
        awaitingResponseChunks = false;
        expectedChunkCount = 0;
        receivedChunks.clear();

        builder.setDeviceState(GBDevice.State.INITIALIZING);

        commandReadCharacteristic = getCharacteristic(ZeblazeConstants.UUID_CHARACTERISTIC_COMMAND_READ);
        commandWriteCharacteristic = getCharacteristic(ZeblazeConstants.UUID_CHARACTERISTIC_COMMAND_WRITE);

        builder.notify(commandReadCharacteristic, true);
        builder.notify(commandWriteCharacteristic, true);

        sendCommand(builder, ZeblazeConstants.CMD_GET_DEVICE_INFO);

        return builder;
    }

    private void sendCommand(final TransactionBuilder builder, final int commandId) {
        pendingOutgoingPayload = ZeblazeProto.ZhMessage.newBuilder()
                .setCommandId(commandId)
                .build()
                .toByteArray();
        sendState = SendState.AWAITING_READY_ACK;
        builder.write(commandWriteCharacteristic, headerFrame(1));
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic,
                                            final byte[] value) {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true;
        }

        final UUID characteristicUUID = characteristic.getUuid();
        if (ZeblazeConstants.UUID_CHARACTERISTIC_COMMAND_WRITE.equals(characteristicUUID)) {
            handleOutgoingAck(value);
            return true;
        } else if (ZeblazeConstants.UUID_CHARACTERISTIC_COMMAND_READ.equals(characteristicUUID)) {
            handleIncomingFrame(value);
            return true;
        }
        return false;
    }

    private void handleOutgoingAck(final byte[] value) {
        switch (sendState) {
            case AWAITING_READY_ACK: {
                if (!Arrays.equals(value, ACK_READY)) {
                    LOG.warn("Expected ready-ack, got {}", Arrays.toString(value));
                    return;
                }
                final TransactionBuilder builder = createTransactionBuilder("zeblaze send command chunk");
                builder.write(commandWriteCharacteristic, dataChunk(1, pendingOutgoingPayload));
                builder.queue();
                sendState = SendState.AWAITING_COMPLETE_ACK;
                break;
            }
            case AWAITING_COMPLETE_ACK:
                if (!Arrays.equals(value, ACK_COMPLETE)) {
                    LOG.warn("Expected complete-ack, got {}", Arrays.toString(value));
                    return;
                }
                sendState = SendState.IDLE;
                pendingOutgoingPayload = null;
                break;
            case IDLE:
            default:
                LOG.warn("Unexpected frame on command-write channel: {}", Arrays.toString(value));
        }
    }

    private void handleIncomingFrame(final byte[] value) {
        if (!awaitingResponseChunks) {
            if (!isHeaderFrame(value)) {
                LOG.warn("Expected header frame, got {}", Arrays.toString(value));
                return;
            }
            expectedChunkCount = chunkCountFromHeader(value);
            receivedChunks.clear();
            awaitingResponseChunks = true;
            final TransactionBuilder builder = createTransactionBuilder("zeblaze response ready-ack");
            builder.write(commandReadCharacteristic, ACK_READY);
            builder.queue();
            return;
        }

        final int index = value[0] & 0xFF;
        receivedChunks.put(index, Arrays.copyOfRange(value, 2, value.length));
        if (receivedChunks.size() < expectedChunkCount) {
            return;
        }

        final byte[] payload = reassembleChunks();
        awaitingResponseChunks = false;

        final TransactionBuilder builder = createTransactionBuilder("zeblaze response complete-ack");
        builder.write(commandReadCharacteristic, ACK_COMPLETE);
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        builder.queue();

        handleDeviceInfoResponse(payload);
    }

    private byte[] reassembleChunks() {
        int totalLength = 0;
        for (final byte[] chunk : receivedChunks.values()) {
            totalLength += chunk.length;
        }
        final ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        for (final byte[] chunk : receivedChunks.values()) {
            buffer.put(chunk);
        }
        return buffer.array();
    }

    private void handleDeviceInfoResponse(final byte[] payload) {
        final ZeblazeProto.DeviceInfo info;
        try {
            info = ZeblazeProto.ZhMessage.parseFrom(payload).getDeviceInfo().getDeviceInfo();
        } catch (final Exception e) {
            LOG.warn("Failed to parse GET_DEVICE_INFO response {}", Arrays.toString(payload), e);
            return;
        }

        final GBDeviceEventVersionInfo versionInfo = new GBDeviceEventVersionInfo();
        versionInfo.fwVersion = info.getFirmwareVersion();
        handleGBDeviceEvent(versionInfo);

        final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
        batteryInfo.level = info.getBattery().getCapacity();
        // Only charge_status == 2 ("not charging") has been confirmed against a real
        // device so far. Treat anything else as charging rather than asserting an
        // unconfirmed mapping.
        batteryInfo.state = info.getBattery().getChargeStatus() == 2 ? BatteryState.BATTERY_NORMAL : BatteryState.BATTERY_CHARGING;
        evaluateGBDeviceEvent(batteryInfo);
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    // ---- ZH_SDK chunked-transport framing ----

    private static byte[] headerFrame(final int chunkCount) {
        return new byte[]{0x00, 0x00, 0x00, 0x00, (byte) (chunkCount & 0xFF), (byte) ((chunkCount >> 8) & 0xFF)};
    }

    private static boolean isHeaderFrame(final byte[] frame) {
        return frame.length == 6
                && frame[0] == HEADER_PREFIX[0] && frame[1] == HEADER_PREFIX[1]
                && frame[2] == HEADER_PREFIX[2] && frame[3] == HEADER_PREFIX[3];
    }

    private static int chunkCountFromHeader(final byte[] frame) {
        return (frame[4] & 0xFF) | ((frame[5] & 0xFF) << 8);
    }

    private static byte[] dataChunk(final int index, final byte[] payload) {
        final byte[] out = new byte[2 + payload.length];
        out[0] = (byte) index;
        out[1] = 0x00;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }
}
