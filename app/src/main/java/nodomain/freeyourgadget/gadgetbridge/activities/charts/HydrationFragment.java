package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.Chart;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

abstract class HydrationFragment<T extends ChartsData> extends AbstractChartFragment<T> {
    protected int TEXT_COLOR;
    protected int CHART_TEXT_COLOR;

    @Override
    public String getTitle() {
        return getString(R.string.pref_header_hydration);
    }

    @Override
    protected void init() {
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    protected static LocalDate toLocalDate(final Date date) {
        return LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getHydrationSampleProvider(device, db.getDaoSession()));
    }
}
