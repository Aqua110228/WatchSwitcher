/*  Copyright (C) 2026 NTeditor, badcpp

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
package nodomain.freeyourgadget.gadgetbridge.util;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class LEB128Utils {
    @NonNull
    public static byte[] encodeUnsigned(long value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(10);
        while (true) {
            byte b = (byte) (value & 0x7F);
            value >>>= 7;
            if (value == 0) {
                baos.write(b);
                break;
            }
            baos.write((byte) (b | 0x80));
        }
        return baos.toByteArray();
    }

    public static long decodeUnsigned(@NonNull ByteBuffer buffer) {
        long result = 0;
        int shift = 0;
        byte b;
        do {
            if (!buffer.hasRemaining()) {
                throw new BufferUnderflowException();
            }
            b = buffer.get();
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }
}
