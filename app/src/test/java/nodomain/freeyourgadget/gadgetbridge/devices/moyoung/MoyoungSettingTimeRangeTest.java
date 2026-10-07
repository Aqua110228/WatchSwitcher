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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.settings.MoyoungSettingTimeRange;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The watch answers with two little endian minute counts, so the decoder has to split each of them
 * into hours and minutes.
 */
public class MoyoungSettingTimeRangeTest extends TestBase {
    private static MoyoungSettingTimeRange.TimeRange decode(final int startMinutes, final int endMinutes) {
        final byte[] payload = {
                (byte) (startMinutes & 0xff), (byte) (startMinutes >> 8),
                (byte) (endMinutes & 0xff), (byte) (endMinutes >> 8),
        };
        return new MoyoungSettingTimeRange("TEST", (byte) 0, (byte) 0).decode(payload);
    }

    @Test
    public void endMinuteComesFromTheEndOfTheRange() {
        // 22:45 to 23:59, chosen so that the two minute fields differ.
        final MoyoungSettingTimeRange.TimeRange range = decode(22 * 60 + 45, 23 * 60 + 59);

        assertEquals("start hour", 22, range.start_h);
        assertEquals("start minute", 45, range.start_m);
        assertEquals("end hour", 23, range.end_h);
        assertEquals("end minute", 59, range.end_m);
    }

    @Test
    public void midnightToMidnightDecodesAsAllZeroes() {
        final MoyoungSettingTimeRange.TimeRange range = decode(0, 0);

        assertEquals("start hour", 0, range.start_h);
        assertEquals("start minute", 0, range.start_m);
        assertEquals("end hour", 0, range.end_h);
        assertEquals("end minute", 0, range.end_m);
    }
}
