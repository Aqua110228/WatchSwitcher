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
package nodomain.freeyourgadget.gadgetbridge.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;

public class BodyCompositionCalculatorTest {
    @Test
    public void testAverageMale() {
        // 30 year old, 180 cm, 80 kg man with a typical impedance
        final BodyCompositionCalculator.BodyComposition c = BodyCompositionCalculator.compute(
                ActivityUser.GENDER_MALE, 30, 180, 80f, 500f
        );
        assertNotNull(c);
        // fat-free mass = -10.68 + 0.65 * 64.8 + 0.26 * 80 + 0.02 * 500 = 62.24 kg
        assertEquals(22.2f, c.bodyFatPercent, 0.1f);
        // body water = 0.99513 * (1.2 + 0.45 * 64.8 + 0.18 * 80) = 44.54 kg
        assertEquals(55.7f, c.bodyWaterPercent, 0.1f);
        // skeletal muscle = 0.401 * 64.8 + 3.825 - 0.071 * 30 + 5.102
        assertEquals(32.8f, c.muscleMassKg, 0.1f);
        assertEquals(3.55f, c.boneMassKg, 0.05f);
        // 21.6 * 62.24 + 370
        assertEquals(1714, c.basalMetabolicRate);
    }

    @Test
    public void testAverageFemale() {
        final BodyCompositionCalculator.BodyComposition c = BodyCompositionCalculator.compute(
                ActivityUser.GENDER_FEMALE, 30, 165, 60f, 600f
        );
        assertNotNull(c);
        // fat-free mass = -9.53 + 0.69 * 45.375 + 0.17 * 60 + 0.02 * 600 = 43.98 kg
        assertEquals(26.7f, c.bodyFatPercent, 0.1f);
        assertEquals(2.2f, c.boneMassKg, 0.05f);
        assertEquals(1320, c.basalMetabolicRate);
    }

    @Test
    public void testOtherGenderIsBetweenMaleAndFemale() {
        final BodyCompositionCalculator.BodyComposition male = BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 40, 170, 70f, 550f);
        final BodyCompositionCalculator.BodyComposition female = BodyCompositionCalculator.compute(ActivityUser.GENDER_FEMALE, 40, 170, 70f, 550f);
        final BodyCompositionCalculator.BodyComposition other = BodyCompositionCalculator.compute(ActivityUser.GENDER_OTHER, 40, 170, 70f, 550f);
        assertNotNull(male);
        assertNotNull(female);
        assertNotNull(other);
        assertEquals((male.bodyFatPercent + female.bodyFatPercent) / 2, other.bodyFatPercent, 0.01f);
        assertEquals((male.muscleMassKg + female.muscleMassKg) / 2, other.muscleMassKg, 0.01f);
    }

    @Test
    public void testRejectsImplausibleInput() {
        assertNull(BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 180, 80f, 50f));
        assertNull(BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 180, 80f, 5000f));
        assertNull(BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 0, 80f, 500f));
        assertNull(BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 0, 180, 80f, 500f));
        // 20 kg at 180 cm: fat-free mass exceeds the weight
        assertNull(BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 180, 20f, 500f));
    }
}
