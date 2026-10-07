/*  Copyright (C) 2026 NTeditor

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointDevice;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.OppoUtils;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoCommand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.MultipointDeviceAction;

public class MultipointDevicesModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(MultipointDevicesModule.class);

    private final ByteOrder macOrder;

    public MultipointDevicesModule(@NonNull Context context, final ByteOrder macOrder) {
        super(context);
        this.macOrder = macOrder;
    }

    @NonNull
    public OppoMessage encodeReq() {
        return new OppoMessage(OppoCommand.MULTIPOINT_DEVICES_REQ, new byte[0]);
    }

    public OppoMessage encodeDeviceAction(@NonNull final String macAddressStr,
            @NonNull final MultipointDeviceAction action) {
        byte[] macAddress = StringUtils.hexToBytes(macAddressStr.replace(":", ""));
        if (macAddress.length != 6) {
            LOG.warn("Unexpected MAC Address length: {}, expected 6", macAddress.length);
            return null;
        }

        if (macOrder == ByteOrder.LITTLE_ENDIAN) {
            macAddress = OppoUtils.bytesReverse(macAddress);
        }

        final ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put((byte) 0x01);
        buf.put(macAddress);
        buf.put((byte) action.getCode());

        LOG.debug("{} for {}", action, macAddressStr);
        return new OppoMessage(OppoCommand.MULTIPOINT_DEVICES_SET, buf.array());
    }

    @NonNull
    public List<MultipointDevice> decodeRet(@NonNull final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        final List<MultipointDevice> devices = new ArrayList<>();

        if (buf.remaining() < 2) {
            LOG.warn("Unexpected multipoint devices ret payload remaining: {}, expected >=2", buf.remaining());
            return devices;
        }

        final byte zero = buf.get();
        final int devicesCount = buf.get() & 0xFF;

        LOG.debug("Got {} multipoint devices", devicesCount);

        for (int i = 0; i < devicesCount; i++) {
            if (buf.remaining() < 10) {
                LOG.warn("Unexpected multipoint devices ret payload remaining: {}, expected >=10", buf.remaining());
                break;
            }

            byte[] macBytes = new byte[6];
            buf.get(macBytes);
            if (macOrder == ByteOrder.LITTLE_ENDIAN) {
                macBytes = OppoUtils.bytesReverse(macBytes);
            }

            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < macBytes.length; b++) {
                sb.append(String.format("%02X", macBytes[b]));
                if (b < macBytes.length - 1) {
                    sb.append(":");
                }
            }
            String macAddress = sb.toString();

            final int reserved = buf.get();
            final boolean isConnected = (buf.get() == 2);
            final boolean isSelf = (buf.get() == 1);

            int nameLength = buf.get() & 0xFF;
            if (buf.remaining() < nameLength) {
                LOG.warn("Unexpected multipoint devices ret payload remaining: {}, expected >={}", buf.remaining(),
                        nameLength);
                break;
            }

            byte[] nameBytes = new byte[nameLength];
            buf.get(nameBytes);
            final String deviceName = new String(nameBytes, StandardCharsets.UTF_8);

            LOG.debug("Got multipoint device {}: MAC = {}, isConnected = {}, isSelf = {}", deviceName, macAddress,
                    isConnected, isSelf);
            devices.add(new MultipointDevice(macAddress, deviceName, isConnected, false, !isSelf));
        }

        devices.sort(Comparator.comparing(MultipointDevice::getCanForget).thenComparing(MultipointDevice::getName,
                Comparator.nullsLast(String::compareTo)));
        return devices;
    }
}
