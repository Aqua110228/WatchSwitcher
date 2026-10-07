/*  Copyright (C) 2024-2026 Severin von Wnuck-Lipinski, oddballza

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
package nodomain.freeyourgadget.gadgetbridge.model;

import androidx.annotation.Nullable;

public interface WeightSample extends TimeSample {
    /**
     * Returns the weight value.
     */
    float getWeightKg();

    /**
     * Returns the raw bio-impedance in Ohms as reported by the scale, or null when the scale
     * has no impedance sensor or this measurement did not carry one.
     */
    @Nullable
    default Integer getImpedanceOhm() {
        return null;
    }
}
