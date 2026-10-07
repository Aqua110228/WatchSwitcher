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

import androidx.annotation.Nullable;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_LUX_HOURS_KILO;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_PERCENTAGE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;

public class SolarChargingDailyFragment extends AbstractChartFragment<SolarChargingDailyFragment.SolarChargingData> {
    protected static final Logger LOG = LoggerFactory.getLogger(SolarChargingDailyFragment.class);

    private TextView mDateView;
    private LinearLayout solarChargingStatsContainer;
    private LineChart solarChargingChart;

    protected int CHART_TEXT_COLOR;
    protected int LEGEND_TEXT_COLOR;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_solar_charging, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.solar_charging_date_view);
        solarChargingStatsContainer = rootView.findViewById(R.id.solar_charging_stats_container);
        solarChargingChart = rootView.findViewById(R.id.solar_charging_chart);
        setupSolarChargingChart();
        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_solar_charging);
    }

    @Override
    protected void init() {
        LEGEND_TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
    }

    @Override
    protected SolarChargingData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        // getTSStart()/getTSEnd() are a rolling 24h window ending "now" (see
        // ActivityChartsActivity), not calendar midnight-to-midnight - recompute the actual
        // start of the displayed day here so both the query and the chart's x-axis agree on
        // the same true midnight, matching what getSolarChargeSamples() queries against.
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(getTSEnd() * 1000L);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        final long dayStartMillis = day.getTimeInMillis();

        List<? extends SolarChargeSample> todaySamples = getSolarChargeSamples(db, device, getTSStart(), getTSEnd());
        return new SolarChargingData(todaySamples, dayStartMillis);
    }

    @Override
    protected void updateChartsnUIThread(SolarChargingData solarChargingData) {
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(getEndDate());
        mDateView.setText(formattedDate);

        // Zero-value samples are not plotted at all (rather than drawn as a point at y=0),
        // and a run of them breaks the line into a separate segment - each non-zero run
        // becomes its own LineDataSet, so MPAndroidChart never bridges across a gap.
        final List<List<Entry>> segments = new ArrayList<>();
        List<Entry> currentSegment = null;
        double totalLuxHours = 0;
        long totalGainMillis = 0;
        float peakPercent = 0f;

        if (!solarChargingData.todaySamples.isEmpty()) {
            final List<? extends SolarChargeSample> samples = solarChargingData.todaySamples;
            totalLuxHours = SolarChargingStats.computeLuxHours(samples);
            totalGainMillis = SolarChargingStats.computeGainMillis(samples);
            // Anchor the x-axis to the actual start of the displayed day (midnight), not the
            // first sample's own timestamp - solar charging has no readings overnight, so the
            // first sample can be hours after midnight, which would otherwise shift every
            // label on the chart by that same offset.
            final long referencedTimestamp = solarChargingData.dayStartMillis;
            for (int i = 0; i < samples.size(); i++) {
                final SolarChargeSample sample = samples.get(i);
                if (sample.getPercent() > peakPercent) {
                    peakPercent = sample.getPercent();
                }
                final float x = (float) sample.getTimestamp() / 1000 - (float) referencedTimestamp / 1000;

                if (sample.getPercent() > 0) {
                    if (currentSegment == null) {
                        currentSegment = new ArrayList<>();
                        segments.add(currentSegment);
                        // Anchor the segment's start at the last known zero reading (real
                        // timestamp, not a fabricated offset) so the line ramps down to the
                        // axis itself rather than starting mid-air - the fill polygon then
                        // already touches zero, instead of relying on the chart library to
                        // extrapolate a closing edge.
                        if (i > 0) {
                            final SolarChargeSample previous = samples.get(i - 1);
                            final float prevX = (float) previous.getTimestamp() / 1000 - (float) referencedTimestamp / 1000;
                            currentSegment.add(new Entry(prevX, 0f));
                        }
                    }
                    currentSegment.add(new Entry(x, sample.getPercent()));
                } else {
                    if (currentSegment != null) {
                        // Anchor the segment's end at this zero reading, for the same reason.
                        currentSegment.add(new Entry(x, 0f));
                    }
                    currentSegment = null;
                }
            }
        }

        final List<ILineDataSet> lineDataSets = new ArrayList<>();
        for (final List<Entry> segment : segments) {
            final LineDataSet lineDataSet = new LineDataSet(segment, getString(R.string.solar_charging_intensity_chart_label));
            lineDataSet.setColor(getResources().getColor(R.color.chart_solar_charging_color));
            lineDataSet.setDrawCircles(false);
            lineDataSet.setLineWidth(2f);
            lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            lineDataSet.setDrawValues(false);
            lineDataSet.setMode(LineDataSet.Mode.LINEAR);
            lineDataSet.setDrawFilled(true);
            lineDataSet.setFillAlpha(255);
            lineDataSet.setFillColor(getResources().getColor(R.color.chart_solar_charging_color));
            lineDataSets.add(lineDataSet);
        }

        final LegendEntry legendEntry = new LegendEntry();
        legendEntry.label = getString(R.string.solar_charging_intensity_chart_label);
        legendEntry.formColor = getResources().getColor(R.color.chart_solar_charging_color);

        solarChargingChart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        solarChargingChart.getLegend().setCustom(Collections.singletonList(legendEntry));

        solarChargingChart.setData(new LineData(lineDataSets));

        final WorkoutValueFormatter unitFormatter = new WorkoutValueFormatter();
        solarChargingStatsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.1f%s", totalLuxHours / 1000.0, unitFormatter.getStringResourceByName(UNIT_LUX_HOURS_KILO)), getString(R.string.solar_charging_lux_hours)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "+ %d %s", totalGainMillis / 60000L, unitFormatter.getStringResourceByName(UNIT_MINUTES)), getString(R.string.solar_charging_battery_gain)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.0f%s", peakPercent, unitFormatter.getStringResourceByName(UNIT_PERCENTAGE)), getString(R.string.solar_charging_peak_intensity)));
        StatTileGridUtilKt.addStatTileGrid(solarChargingStatsContainer, requireContext(), stats, 0);
    }

    @Override
    protected void renderCharts() {
        solarChargingChart.invalidate();
    }

    /**
     * Get solar charging samples for the calendar day containing tsTo (not the raw
     * tsFrom/tsTo range itself, which is a rolling 24h window, not midnight-to-midnight).
     */
    public List<? extends SolarChargeSample> getSolarChargeSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(tsTo * 1000L); // we need today initially, which is the end of the time range
        day.set(Calendar.HOUR_OF_DAY, 0); // and we set time for the start and end of the same day
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        tsFrom = (int) (day.getTimeInMillis() / 1000);
        tsTo = tsFrom + 24 * 60 * 60 - 1;

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends SolarChargeSample> sampleProvider = coordinator.getSolarChargeSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    private void setupSolarChargingChart() {
        solarChargingChart.getDescription().setEnabled(false);
        solarChargingChart.setTouchEnabled(false);
        solarChargingChart.setPinchZoom(false);
        solarChargingChart.setDoubleTapToZoomEnabled(false);

        final XAxis xAxisBottom = solarChargingChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);
        xAxisBottom.setAxisMinimum(0f);
        xAxisBottom.setAxisMaximum(86400f);
        xAxisBottom.setLabelCount(7, true);
        xAxisBottom.setValueFormatter(getSolarChargingChartXValueFormatter());

        final YAxis yAxisLeft = solarChargingChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMaximum(100);
        yAxisLeft.setAxisMinimum(0);
        yAxisLeft.setDrawTopYLabelEntry(true);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisRight = solarChargingChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }

    ValueFormatter getSolarChargingChartXValueFormatter() {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long timestamp = (long) (value * 1000);
                Date date = new Date();
                date.setTime(timestamp);
                SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                return df.format(date);
            }
        };
    }

    protected static class SolarChargingData extends ChartsData {
        private final List<? extends SolarChargeSample> todaySamples;
        private final long dayStartMillis;

        protected SolarChargingData(List<? extends SolarChargeSample> todaySamples, long dayStartMillis) {
            this.todaySamples = todaySamples;
            this.dayStartMillis = dayStartMillis;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getSolarChargeSampleProvider(device, db.getDaoSession()));
    }
}
