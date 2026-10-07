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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoCommand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.MiscConfigType;

public class MiscConfigModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(MiscConfigModule.class);

    public MiscConfigModule(@NonNull final Context context) {
        super(context);
    }

    public OppoMessage encodeReq(@NonNull final MiscConfigType... types) {
        if (types.length == 0) {
            return null;
        }

        final ByteBuffer buf = ByteBuffer.allocate(1 + types.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) types.length);
        for (MiscConfigType type : types) {
            buf.put((byte) type.getCode());
        }

        return new OppoMessage(OppoCommand.MISC_CONFIG_REQ, buf.array());
    }

    public OppoMessage encodeReq(@NonNull final EnumSet<MiscConfigType> types) {
        return encodeReq(types.toArray(new MiscConfigType[0]));
    }

    @NonNull
    public OppoMessage encodeSet(@NonNull final MiscConfigType type, final boolean value) {
        LOG.debug("Send {} = {}", type, value);
        final byte[] payload = new byte[] {
            (byte) type.getCode(),
            (byte) (value ? 0x01 : 0x00),
        };

        return new OppoMessage(OppoCommand.MISC_CONFIG_SET, payload);
    }

    @NonNull
    public Map<MiscConfigType, Boolean> decodeRet(@NonNull final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        final Map<MiscConfigType, Boolean> result = new HashMap<>();

        if (buf.remaining() < 2) {
            LOG.warn("Unexpected misc config ret payload remaining {}, expected >=2", buf.remaining());
            return result;
        }

        final int zero = buf.get();
        final int numTypes = buf.get() & 0xFF;

        for (int i = 0; i < numTypes; i++) {
            if (buf.remaining() < 2) {
                LOG.warn("Unexpected misc config ret payload remaining {}, expected >= 2", buf.remaining());
                break;
            }

            final int typeCode = buf.get() & 0xFF;
            final int valueCode = buf.get() & 0xFF;
            final boolean value = (valueCode == 1);

            final MiscConfigType type = MiscConfigType.fromCode(typeCode);
            if (type == null) {
                LOG.warn("Unknown misc config type code {}", typeCode);
                continue;
            }

            LOG.debug("Got {} = {}", type, value);
            result.put(type, value);
        }

        return result;
    }
}
