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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.RacePredictionFragmentAdapter;

public class RacePredictionCollectionFragment extends AbstractCollectionFragment {
    public RacePredictionCollectionFragment() {
    }

    public static RacePredictionCollectionFragment newInstance(final boolean allowSwipe) {
        final RacePredictionCollectionFragment fragment = new RacePredictionCollectionFragment();
        final Bundle args = new Bundle();
        args.putBoolean(ARG_ALLOW_SWIPE, allowSwipe);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public FragmentStateAdapter getFragmentAdapter() {
        return new RacePredictionFragmentAdapter(this);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        final TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(getString(R.string.calendar_month));
                    break;
                case 1:
                    tab.setText(getString(R.string.six_months));
                    break;
                case 2:
                    tab.setText(getString(R.string.calendar_year));
                    break;
            }
        }).attach();
    }
}
