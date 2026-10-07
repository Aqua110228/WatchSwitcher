/*  Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.fieldDefinitions;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FieldDefinition;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.baseTypes.BaseType;

public class FieldDefinitionDayOfWeek extends FieldDefinition {

    public FieldDefinitionDayOfWeek(int localNumber, int size, BaseType baseType, String name, double scale, int offset) {
        super(localNumber, size, baseType, name, scale, offset);
    }

    @Nullable
    @Override
    public Object decode(ByteBuffer byteBuffer) {
        final Object rawObj = baseType.decode(byteBuffer, scale, offset);
        if (rawObj instanceof final Number number) {
            final int raw = number.intValue();
            return DayOfWeek.of(raw == 0 ? 7 : raw);
        }
        return null;
    }

    @Override
    public void encode(ByteBuffer byteBuffer, Object o) {
        if (o instanceof final Number number) {
            long epoc = number.longValue();
            Instant instant = Instant.ofEpochSecond(epoc);
            ZoneId zoneId = ZoneId.systemDefault();
            ZonedDateTime zonedDateTime = instant.atZone(zoneId);
            o = zonedDateTime.getDayOfWeek();
        }

        if (o instanceof final DayOfWeek dayOfWeek) {
            o = dayOfWeek.getValue() % 7;
        } else {
            o = null;
        }

        baseType.encode(byteBuffer, o, scale, offset);
    }
}
