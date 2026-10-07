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

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;

/**
 * Estimates body composition from a single-frequency bio-impedance measurement, as reported by
 * consumer BIA scales, using published prediction equations rather than any vendor's proprietary
 * curves. The estimates are only as good as the impedance reading: consumer scales are imprecise
 * and hydration changes the impedance considerably, so the values are mostly useful to track
 * trends under consistent measurement conditions.
 * <p>
 * Equations:
 * <ul>
 *     <li>Fat-free mass and total body water: Sun SS et al., "Development of bioelectrical
 *     impedance analysis prediction equations for body composition with the use of a
 *     multicomponent model for use in epidemiologic surveys", Am J Clin Nutr 2003;77:331-40.</li>
 *     <li>Skeletal muscle mass: Janssen I et al., "Estimation of skeletal muscle mass by
 *     bioelectrical impedance analysis", J Appl Physiol 2000;89:465-71.</li>
 *     <li>Basal metabolic rate: Katch-McArdle, from fat-free mass.</li>
 * </ul>
 * The approach mirrors openScale's StandardImpedanceLib (GPLv3).
 */
public class BodyCompositionCalculator {
    /** Sun et al. equations were validated with impedances roughly in this range. */
    private static final float MIN_IMPEDANCE_OHM = 200f;
    private static final float MAX_IMPEDANCE_OHM = 1200f;

    /** Fraction of fat-free mass that is bone mineral, commonly used by BIA devices. */
    private static final double BONE_FRACTION_MALE = 0.057;
    private static final double BONE_FRACTION_FEMALE = 0.05;

    /** Density of water at body temperature, to convert litres to kg. */
    private static final double WATER_KG_PER_LITRE = 0.99513;

    public static class BodyComposition {
        public final float bodyFatPercent;
        public final float bodyWaterPercent;
        public final float muscleMassKg;
        public final float boneMassKg;
        public final int basalMetabolicRate;

        BodyComposition(final float bodyFatPercent,
                        final float bodyWaterPercent,
                        final float muscleMassKg,
                        final float boneMassKg,
                        final int basalMetabolicRate) {
            this.bodyFatPercent = bodyFatPercent;
            this.bodyWaterPercent = bodyWaterPercent;
            this.muscleMassKg = muscleMassKg;
            this.boneMassKg = boneMassKg;
            this.basalMetabolicRate = basalMetabolicRate;
        }
    }

    /**
     * @return the estimated body composition for the current user, or null when it cannot be
     * estimated (missing user profile, implausible impedance, or nonsensical result).
     */
    @Nullable
    public static BodyComposition compute(final ActivityUser user, final float weightKg, final float impedanceOhm) {
        return compute(user.getGender(), user.getAge(), user.getHeightCm(), weightKg, impedanceOhm);
    }

    /**
     * @param gender one of {@link ActivityUser#GENDER_MALE}, {@link ActivityUser#GENDER_FEMALE},
     *               {@link ActivityUser#GENDER_OTHER}. The equations are sex-specific; for
     *               {@link ActivityUser#GENDER_OTHER} the average of both is used.
     */
    @Nullable
    public static BodyComposition compute(final int gender,
                                          final int ageYears,
                                          final int heightCm,
                                          final float weightKg,
                                          final float impedanceOhm) {
        if (heightCm <= 0 || ageYears <= 0 || weightKg <= 0) {
            return null;
        }
        if (impedanceOhm < MIN_IMPEDANCE_OHM || impedanceOhm > MAX_IMPEDANCE_OHM) {
            return null;
        }

        final double maleWeight;
        switch (gender) {
            case ActivityUser.GENDER_MALE:
                maleWeight = 1.0;
                break;
            case ActivityUser.GENDER_FEMALE:
                maleWeight = 0.0;
                break;
            default:
                maleWeight = 0.5;
        }

        // height² / resistance, the impedance index all the equations are built on
        final double h2r = (double) heightCm * heightCm / impedanceOhm;

        final double fatFreeMassMale = -10.68 + 0.65 * h2r + 0.26 * weightKg + 0.02 * impedanceOhm;
        final double fatFreeMassFemale = -9.53 + 0.69 * h2r + 0.17 * weightKg + 0.02 * impedanceOhm;
        final double fatFreeMassKg = blend(fatFreeMassMale, fatFreeMassFemale, maleWeight);

        final double bodyWaterMaleLitres = 1.2 + 0.45 * h2r + 0.18 * weightKg;
        final double bodyWaterFemaleLitres = 3.75 + 0.45 * h2r + 0.11 * weightKg;
        final double bodyWaterKg = WATER_KG_PER_LITRE * blend(bodyWaterMaleLitres, bodyWaterFemaleLitres, maleWeight);

        final double skeletalMuscleMassKg = 0.401 * h2r + 3.825 * maleWeight - 0.071 * ageYears + 5.102;
        final double boneMassKg = blend(BONE_FRACTION_MALE, BONE_FRACTION_FEMALE, maleWeight) * fatFreeMassKg;
        final double basalMetabolicRate = 21.6 * fatFreeMassKg + 370;

        final double bodyFatPercent = 100.0 * (1.0 - fatFreeMassKg / weightKg);
        final double bodyWaterPercent = 100.0 * bodyWaterKg / weightKg;

        if (bodyFatPercent <= 0 || bodyFatPercent >= 100 || bodyWaterPercent <= 0 || bodyWaterPercent >= 100
                || skeletalMuscleMassKg <= 0 || skeletalMuscleMassKg >= weightKg) {
            return null;
        }

        return new BodyComposition(
                (float) bodyFatPercent,
                (float) bodyWaterPercent,
                (float) skeletalMuscleMassKg,
                (float) boneMassKg,
                (int) Math.round(basalMetabolicRate)
        );
    }

    private static double blend(final double male, final double female, final double maleWeight) {
        return male * maleWeight + female * (1.0 - maleWeight);
    }
}
