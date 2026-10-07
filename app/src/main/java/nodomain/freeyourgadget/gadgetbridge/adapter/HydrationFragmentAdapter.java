package nodomain.freeyourgadget.gadgetbridge.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import nodomain.freeyourgadget.gadgetbridge.activities.charts.HydrationDailyFragment;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.HydrationPeriodFragment;

public class HydrationFragmentAdapter extends NestedFragmentAdapter {

    public HydrationFragmentAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 1 -> HydrationPeriodFragment.newInstance(7);
            case 2 -> HydrationPeriodFragment.newInstance(30);
            default -> new HydrationDailyFragment();
        };
    }
}
