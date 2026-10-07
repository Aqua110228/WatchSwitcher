/*  Copyright (C) 2026 Dany Mestas

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

import org.junit.Test;

/**
 * A lazy hexdump stands in for the eager one in log statements, so it has to render identically.
 */
public class GBLazyHexdumpTest {
    private static final byte[] BYTES = {0x0a, 0x01, (byte) 0xff, 0x10};

    @Test
    public void wholeArrayMatchesTheEagerDump() {
        assertEquals(GB.hexdump(BYTES), GB.lazyHexdump(BYTES).toString());
    }

    @Test
    public void rangeMatchesTheEagerDump() {
        assertEquals(GB.hexdump(BYTES, 1, 2), GB.lazyHexdump(BYTES, 1, 2).toString());
        assertEquals(GB.hexdump(BYTES, 1, -1), GB.lazyHexdump(BYTES, 1, -1).toString());
    }

    @Test
    public void emptyArrayRendersEmpty() {
        assertEquals("", GB.lazyHexdump(new byte[0]).toString());
    }

    @Test
    public void nullRendersLikeTheEagerDump() {
        assertEquals(GB.hexdump(null), GB.lazyHexdump(null).toString());
    }
}
