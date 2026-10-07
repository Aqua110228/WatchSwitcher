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
package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericImpedanceSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * A scale can report several bio-impedance readings at the same instant, one per body section and
 * measurement frequency. The table has to keep them apart rather than let one overwrite another.
 * The values are synthetic, not readings from a person.
 */
public class GenericImpedanceSampleProviderTest extends TestBase {
    private GBDevice device;
    private GenericImpedanceSampleProvider provider;

    @Before
    public void setUpProvider() {
        device = createDummyGDevice("00:00:00:00:00:21");
        // the provider only persists for a device it can find in the database
        DBHelper.getDevice(device, daoSession);
        provider = new GenericImpedanceSampleProvider(device, daoSession);
    }

    private static GenericImpedanceSample reading(final long timestamp, final int section, final int frequencyHz, final float ohm) {
        final GenericImpedanceSample sample = new GenericImpedanceSample();
        sample.setTimestamp(timestamp);
        sample.setBodySectionIdx(section);
        sample.setFrequencyHz(frequencyHz);
        sample.setImpedanceOhm(ohm);
        return sample;
    }

    /** The readings stored at one instant, ordered by body section and then frequency. */
    private static List<GenericImpedanceSample> storedAt(final GenericImpedanceSampleProvider provider, final long timestamp) {
        final List<GenericImpedanceSample> samples = new ArrayList<>(provider.getAllSamples(timestamp, timestamp));
        samples.sort(Comparator.comparingInt(GenericImpedanceSample::getBodySectionIdx)
                .thenComparingInt(GenericImpedanceSample::getFrequencyHz));
        return samples;
    }

    @Test
    public void readingsAtTheSameInstantAreKeptApart() {
        final long t = 1_700_000_000_000L;
        provider.persistSamples(Arrays.asList(
                reading(t, 0, 50_000, 501.5f),
                reading(t, 0, 250_000, 455.5f),
                reading(t, 1, 50_000, 301.5f)
        ), getContext());

        final List<GenericImpedanceSample> stored = storedAt(provider, t);
        assertEquals("one row per body section and frequency", 3, stored.size());
        assertEquals(501.5f, stored.get(0).getImpedanceOhm(), 0.0f);
        assertEquals(455.5f, stored.get(1).getImpedanceOhm(), 0.0f);
        assertEquals(301.5f, stored.get(2).getImpedanceOhm(), 0.0f);
    }

    @Test
    public void fractionalKilohertzFrequenciesAreKeptApart() {
        // Scales measure at fractional kHz: 6.25 kHz is 6250 Hz exactly, and would collapse into
        // the neighbouring 6 kHz reading if the column counted whole kHz.
        final long t = 1_700_000_400_000L;
        provider.persistSamples(Arrays.asList(
                reading(t, 0, 6_000, 470.0f),
                reading(t, 0, 6_250, 468.5f)
        ), getContext());

        final List<GenericImpedanceSample> stored = storedAt(provider, t);
        assertEquals(2, stored.size());
        assertEquals(6_000, stored.get(0).getFrequencyHz());
        assertEquals(6_250, stored.get(1).getFrequencyHz());
        assertEquals(468.5f, stored.get(1).getImpedanceOhm(), 0.0f);
    }

    @Test
    public void anUnknownFrequencyIsStoredAsMinusOne() {
        final long t = 1_700_000_100_000L;
        provider.persistSamples(Collections.singletonList(reading(t, 0, -1, 480.0f)), getContext());

        final List<GenericImpedanceSample> stored = storedAt(provider, t);
        assertEquals(1, stored.size());
        assertEquals(-1, stored.get(0).getFrequencyHz());
    }

    @Test
    public void theSameReadingAgainReplacesRatherThanDuplicates() {
        final long t = 1_700_000_200_000L;
        provider.persistSamples(Collections.singletonList(reading(t, 0, 50_000, 500.0f)), getContext());
        provider.persistSamples(Collections.singletonList(reading(t, 0, 50_000, 512.5f)), getContext());

        final List<GenericImpedanceSample> stored = storedAt(provider, t);
        assertEquals(1, stored.size());
        assertEquals(512.5f, stored.get(0).getImpedanceOhm(), 0.0f);
    }

    @Test
    public void readingsBelongToTheirOwnDevice() {
        final long t = 1_700_000_300_000L;
        provider.persistSamples(Collections.singletonList(reading(t, 0, 50_000, 500.0f)), getContext());

        final GBDevice other = createDummyGDevice("00:00:00:00:00:22");
        DBHelper.getDevice(other, daoSession);
        final GenericImpedanceSampleProvider otherProvider = new GenericImpedanceSampleProvider(other, daoSession);

        assertEquals(0, storedAt(otherProvider, t).size());
        assertEquals(1, storedAt(provider, t).size());
    }
}
