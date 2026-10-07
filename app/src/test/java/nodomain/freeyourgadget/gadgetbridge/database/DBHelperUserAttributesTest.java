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
package nodomain.freeyourgadget.gadgetbridge.database;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.UserAttributes;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class DBHelperUserAttributesTest extends TestBase {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    // Two records, the way DBHelper.ensureUserAttributes writes them: the old one is closed one
    // minute before the new one starts. The list is newest first, like User.getUserAttributesList().
    private static final long OLD_FROM = 1_000L * DAY;
    private static final long NEW_FROM = 2_000L * DAY;
    private static final UserAttributes OLD = attributes(160, OLD_FROM, NEW_FROM - MINUTE);
    private static final UserAttributes NEW = attributes(180, NEW_FROM, null);
    private static final List<UserAttributes> HISTORY = Arrays.asList(NEW, OLD);

    private static UserAttributes attributes(final int heightCm, final long fromMillis, final Long toMillis) {
        final UserAttributes a = new UserAttributes();
        a.setHeightCM(heightCm);
        a.setValidFromUTC(new Date(fromMillis));
        a.setValidToUTC(toMillis != null ? new Date(toMillis) : null);
        return a;
    }

    @Test
    public void picksTheRecordInEffectAtThatTime() {
        assertSame(OLD, DBHelper.selectAttributesAt(HISTORY, new Date(OLD_FROM + 100 * DAY)));
        assertSame(NEW, DBHelper.selectAttributesAt(HISTORY, new Date(NEW_FROM + 100 * DAY)));
    }

    @Test
    public void closesTheGapBetweenRecordsWithThePreviousOne() {
        assertSame(OLD, DBHelper.selectAttributesAt(HISTORY, new Date(NEW_FROM - MINUTE / 2)));
    }

    @Test
    public void fallsBackToTheEarliestRecordBeforeAnyWasKept() {
        assertSame(OLD, DBHelper.selectAttributesAt(HISTORY, new Date(OLD_FROM - 100 * DAY)));
    }

    @Test
    public void hasNothingToOfferWithoutRecords() {
        assertNull(DBHelper.selectAttributesAt(Collections.emptyList(), new Date(NEW_FROM)));
        assertNull(DBHelper.selectAttributesAt(null, new Date(NEW_FROM)));
    }
}
