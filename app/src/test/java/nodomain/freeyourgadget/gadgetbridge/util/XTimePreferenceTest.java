/*  Copyright (C) 2026 Freeyourgadget

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

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class XTimePreferenceTest extends TestBase {
    private XTimePreference preference;

    @Before
    public void setUp() {
        preference = new XTimePreference(app, null);
        preference.setFormat(XTimePreference.Format.FORMAT_12H);
    }

    private String summaryFor(final int hour, final int minute) {
        preference.hour = hour;
        preference.minute = minute;
        preference.updateSummary();
        return preference.getSummary().toString();
    }

    @Test
    public void midnightIsDisplayedAsTwelveAm() {
        assertEquals("12:00 AM", summaryFor(0, 0));
        assertEquals("12:30 AM", summaryFor(0, 30));
    }

    @Test
    public void noonAndAfternoonAreDisplayedWithPmSuffix() {
        assertEquals("12:00 PM", summaryFor(12, 0));
        assertEquals("1:45 PM", summaryFor(13, 45));
        assertEquals("11:59 PM", summaryFor(23, 59));
    }
}
