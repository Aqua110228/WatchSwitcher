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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.entities.UserAttributes;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator;
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionEstimates;

public class WeightChartFragmentTest extends TestBase {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    // Synthetic profile and reading; nothing here is a real person's measurement.
    private static final int HEIGHT_THEN_CM = 160;
    private static final int HEIGHT_NOW_CM = 180;
    private static final float WEIGHT_KG = 80f;
    private static final int IMPEDANCE_OHM = 500;

    /**
     * Profile says HEIGHT_NOW_CM (valid from now); an older record says the user was
     * HEIGHT_THEN_CM for the years before. Returns a measurement taken in that earlier period.
     */
    private GenericWeightSample measurementTakenWhenShorter(final Integer impedanceOhm) {
        GBApplication.getPrefs().getPreferences().edit()
                .putString(ActivityUser.PREF_USER_HEIGHT_CM, String.valueOf(HEIGHT_NOW_CM))
                .apply();
        final User user = DBHelper.getUser(daoSession);
        final long now = System.currentTimeMillis();
        final UserAttributes earlier = new UserAttributes();
        earlier.setUserId(user.getId());
        earlier.setHeightCM(HEIGHT_THEN_CM);
        earlier.setWeightKG(0);
        earlier.setValidFromUTC(new Date(now - 2_000 * DAY));
        earlier.setValidToUTC(new Date(now - 2 * MINUTE));
        daoSession.getUserAttributesDao().insert(earlier);
        user.resetUserAttributesList();

        final GenericWeightSample sample = new GenericWeightSample();
        sample.setTimestamp(now - 1_000 * DAY);
        sample.setWeightKg(WEIGHT_KG);
        sample.setImpedanceOhm(impedanceOhm);
        return sample;
    }

    @Test
    public void bmiUsesTheHeightRecordedAtTheTimeOfTheMeasurement() {
        final GenericWeightSample sample = measurementTakenWhenShorter(null);

        final Float bmi = BodyCompositionEstimates.bmi(daoSession, sample);

        assertNotNull(bmi);
        assertEquals(WEIGHT_KG / (1.6f * 1.6f), bmi, 0.001f);
        assertNotEquals(WEIGHT_KG / (1.8f * 1.8f), bmi, 0.01f);
        // BMI needs no impedance, unlike the rest of the estimates
        assertNull(BodyCompositionEstimates.composition(daoSession, sample));
        assertNull(BodyCompositionEstimates.bmi(daoSession, null));
    }

    @Test
    public void estimatesWithTheHeightRecordedAtTheTimeOfTheMeasurement() {
        final GenericWeightSample sample = measurementTakenWhenShorter(IMPEDANCE_OHM);
        final long measuredAt = sample.getTimestamp();

        final BodyCompositionCalculator.BodyComposition actual = BodyCompositionEstimates.composition(daoSession, sample);

        final ActivityUser prefsUser = new ActivityUser();
        final int ageThen = prefsUser.getAgeAt(Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()).toLocalDate());
        final BodyCompositionCalculator.BodyComposition withHeightThen =
                BodyCompositionCalculator.compute(prefsUser.getGender(), ageThen, HEIGHT_THEN_CM, WEIGHT_KG, IMPEDANCE_OHM);
        final BodyCompositionCalculator.BodyComposition withHeightNow =
                BodyCompositionCalculator.compute(prefsUser.getGender(), ageThen, HEIGHT_NOW_CM, WEIGHT_KG, IMPEDANCE_OHM);

        assertNotNull(actual);
        assertNotNull(withHeightThen);
        assertNotNull(withHeightNow);
        assertNotEquals("the two heights must give different estimates for this check to mean anything",
                withHeightThen.bodyFatPercent, withHeightNow.bodyFatPercent, 0.01f);
        assertEquals(withHeightThen.bodyFatPercent, actual.bodyFatPercent, 0.001f);
        assertEquals(withHeightThen.basalMetabolicRate, actual.basalMetabolicRate);
    }

    @Test
    public void hidingAValueLetsTheFollowingOnesMoveUp() {
        final Context themed = new ContextThemeWrapper(getContext(), R.style.GadgetbridgeTheme);
        final View root = LayoutInflater.from(themed).inflate(R.layout.fragment_weightchart, null);
        final View weight = tileOf(root, R.id.weight_latest_text);
        // the values are hidden until a measurement provides them; show them all, in their order
        final View[] values = new View[6];
        final int[] ids = {R.id.weight_body_fat_text, R.id.weight_body_water_text, R.id.weight_muscle_mass_text,
                R.id.weight_bone_mass_text, R.id.weight_bmr_text, R.id.weight_impedance_text};
        for (int i = 0; i < ids.length; i++) {
            values[i] = tileOf(root, ids[i]);
            values[i].setVisibility(View.VISIBLE);
        }

        layOut(root);
        // The first value that starts a new row, and the one before it (the last of the previous row).
        int rowStart = -1;
        for (int i = 1; i < values.length; i++) {
            if (positionRelativeTo(root, weight, values[i])[1] != positionRelativeTo(root, weight, values[i - 1])[1]) {
                rowStart = i;
                break;
            }
        }
        assertTrue("the values must span more than one row for this check to mean anything", rowStart > 0);
        final int[] endOfPreviousRow = positionRelativeTo(root, weight, values[rowStart - 1]);

        // Hide the first value: everything after it shifts back by one cell, so the value that
        // started the row has to move up into the previous row, not just slide within its own.
        values[0].setVisibility(View.GONE);
        layOut(root);
        final int[] now = positionRelativeTo(root, weight, values[rowStart]);
        assertEquals("row", endOfPreviousRow[1], now[1]);
        assertEquals("column", endOfPreviousRow[0], now[0]);
    }

    /** The tile (line, value, label) holding a value view. */
    private static View tileOf(final View root, final int valueId) {
        final View value = root.findViewById(valueId);
        assertNotNull(value);
        return (View) value.getParent();
    }

    private static void layOut(final View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2400);
    }

    /** Left and top of a tile, relative to another tile (the grid itself moves as the chart above it resizes). */
    private static int[] positionRelativeTo(final View root, final View origin, final View view) {
        return new int[]{view.getLeft() - origin.getLeft(), topWithin(root, view) - topWithin(root, origin)};
    }

    private static int topWithin(final View root, final View view) {
        int top = 0;
        for (View v = view; v != root; v = (View) v.getParent()) {
            top += v.getTop();
        }
        return top;
    }

    @Test
    public void hasNoEstimateWithoutImpedance() {
        final GenericWeightSample sample = new GenericWeightSample();
        sample.setTimestamp(System.currentTimeMillis());
        sample.setWeightKg(WEIGHT_KG);
        sample.setImpedanceOhm(null);

        assertNull(BodyCompositionEstimates.composition(daoSession, sample));
        assertNull(BodyCompositionEstimates.composition(daoSession, null));
    }
}
