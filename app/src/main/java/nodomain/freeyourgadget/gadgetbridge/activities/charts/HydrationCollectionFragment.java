package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.os.Bundle;

import androidx.viewpager2.adapter.FragmentStateAdapter;

import nodomain.freeyourgadget.gadgetbridge.adapter.HydrationFragmentAdapter;

public class HydrationCollectionFragment extends AbstractCollectionFragment {
    public HydrationCollectionFragment() {

    }

    public static HydrationCollectionFragment newInstance(final boolean allowSwipe) {
        final HydrationCollectionFragment fragment = new HydrationCollectionFragment();
        final Bundle args = new Bundle();
        args.putBoolean(ARG_ALLOW_SWIPE, allowSwipe);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public FragmentStateAdapter getFragmentAdapter() {
        return new HydrationFragmentAdapter(this);
    }
}
