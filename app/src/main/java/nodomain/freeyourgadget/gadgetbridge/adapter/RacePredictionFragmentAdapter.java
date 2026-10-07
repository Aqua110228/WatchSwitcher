/*  Copyright (C) 2026 a0z

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
package nodomain.freeyourgadget.gadgetbridge.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import nodomain.freeyourgadget.gadgetbridge.activities.charts.RacePredictionPeriodFragment;

public class RacePredictionFragmentAdapter extends NestedFragmentAdapter {
    public RacePredictionFragmentAdapter(final Fragment fragment) {
        super(fragment);
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    @NonNull
    @Override
    public Fragment createFragment(final int position) {
        switch (position) {
            case 1:
                return RacePredictionPeriodFragment.newInstance(180, false);
            case 2:
                return RacePredictionPeriodFragment.newInstance(365, false);
        }
        return RacePredictionPeriodFragment.newInstance(30, true);
    }
}
