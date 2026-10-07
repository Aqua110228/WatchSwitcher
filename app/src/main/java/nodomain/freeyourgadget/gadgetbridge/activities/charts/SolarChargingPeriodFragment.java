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

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_LUX_HOURS_KILO;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;

public class SolarChargingPeriodFragment extends AbstractChartFragment<SolarChargingPeriodFragment.SolarChargingPeriodData> {
    protected static final Logger LOG = LoggerFactory.getLogger(SolarChargingPeriodFragment.class);

    private static final String STATE_SELECTED_METRIC = "selectedMetric";
    private static final SolarMetric DEFAULT_METRIC = SolarMetric.LUX_HOURS;
    private static final int SEC_PER_DAY = 24 * 60 * 60;

    private enum SolarMetric {
        // yAxisMinScale: the y-axis maximum never drops below this, so a quiet
        // day/week doesn't render as a chart with barely any visible scale.
        LUX_HOURS(R.string.solar_charging_lux_hours, "K", 50f),
        BATTERY_GAIN(R.string.solar_charging_battery_gain, "mins", 60f);

        final int labelResId;
        final String unit;
        final float yAxisMinScale;

        SolarMetric(final int labelResId, final String unit, final float yAxisMinScale) {
            this.labelResId = labelResId;
            this.unit = unit;
            this.yAxisMinScale = yAxisMinScale;
        }
    }

    private int TOTAL_DAYS;
    private SolarMetric selectedMetric = DEFAULT_METRIC;

    private TextView mDateView;
    private LinearLayout statsContainer;
    private BarChart chart;
    private ChipGroup metricChipGroup;

    protected int CHART_TEXT_COLOR;
    protected int LEGEND_TEXT_COLOR;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static SolarChargingPeriodFragment newInstance(final int totalDays) {
        final SolarChargingPeriodFragment fragment = new SolarChargingPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 7;
    }

    @Override
    protected void init() {
        LEGEND_TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_solar_charging_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.solar_charging_period_date_view);
        statsContainer = rootView.findViewById(R.id.solar_charging_period_stats_container);
        chart = rootView.findViewById(R.id.solar_charging_period_chart);
        metricChipGroup = rootView.findViewById(R.id.solar_charging_period_chip_group);

        if (savedInstanceState != null) {
            final String savedMetricName = savedInstanceState.getString(STATE_SELECTED_METRIC);
            if (savedMetricName != null) {
                try {
                    selectedMetric = SolarMetric.valueOf(savedMetricName);
                } catch (final IllegalArgumentException ignored) {
                    selectedMetric = DEFAULT_METRIC;
                }
            }
        }

        setupMetricChips(inflater);
        setupChart();
        refresh();

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_METRIC, selectedMetric.name());
    }

    private void setupMetricChips(final LayoutInflater inflater) {
        metricChipGroup.removeAllViews();
        for (final SolarMetric metric : SolarMetric.values()) {
            final Chip chip = (Chip) inflater.inflate(R.layout.layout_chart_chip, metricChipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(getString(metric.labelResId));
            chip.setTag(metric);
            metricChipGroup.addView(chip);
            chip.setChecked(metric == selectedMetric);
        }
        metricChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            final Chip checkedChip = group.findViewById(checkedIds.get(0));
            final SolarMetric metric = (SolarMetric) checkedChip.getTag();
            if (metric != selectedMetric) {
                selectedMetric = metric;
                refresh();
            }
        });
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_solar_charging);
    }

    private int getStartTs() {
        final Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        return (int) (day.getTimeInMillis() / 1000) - SEC_PER_DAY * (TOTAL_DAYS - 1);
    }

    private List<? extends SolarChargeSample> getSamples(final DBHandler db, final GBDevice device, final int tsFrom, final int tsTo) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends SolarChargeSample> sampleProvider = coordinator.getSolarChargeSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    private DayData fetchDayData(final DBHandler db, final GBDevice device, final int dayStartTs) {
        final int dayEndTs = dayStartTs + SEC_PER_DAY - 1;
        final List<? extends SolarChargeSample> samples = getSamples(db, device, dayStartTs, dayEndTs);
        final double luxHours = SolarChargingStats.computeLuxHours(samples);
        final double gainMinutes = SolarChargingStats.computeGainMillis(samples) / 60000.0;
        return new DayData(luxHours, gainMinutes);
    }

    @Override
    protected SolarChargingPeriodData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final int startTs = getStartTs();
        final List<DayData> days = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            days.add(fetchDayData(db, device, startTs + i * SEC_PER_DAY));
        }
        return new SolarChargingPeriodData(days, selectedMetric);
    }

    @Override
    protected void updateChartsnUIThread(final SolarChargingPeriodData data) {
        // The chip may have been flipped again while this refresh's background query was
        // still in flight - discard a result that no longer matches the current selection.
        if (data.metric != selectedMetric) {
            return;
        }

        final int startTs = getStartTs();
        mDateView.setText(new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date(startTs * 1000L))
                + " - " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(getEndDate()));

        final Accumulator luxHoursAccumulator = new Accumulator();
        final Accumulator gainMinutesAccumulator = new Accumulator();
        final List<BarEntry> entries = new ArrayList<>();

        for (int i = 0; i < data.days.size(); i++) {
            final DayData dayData = data.days.get(i);
            luxHoursAccumulator.add(dayData.luxHours);
            gainMinutesAccumulator.add(dayData.gainMinutes);

            final double value = selectedMetric == SolarMetric.LUX_HOURS
                    ? dayData.luxHours / 1000.0
                    : dayData.gainMinutes;
            entries.add(new BarEntry(i, (float) value));
        }

        final WorkoutValueFormatter unitFormatter = new WorkoutValueFormatter();
        final String kiloLuxHoursUnit = unitFormatter.getStringResourceByName(UNIT_LUX_HOURS_KILO);
        final String minutesUnit = unitFormatter.getStringResourceByName(UNIT_MINUTES);
        statsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.1f%s", luxHoursAccumulator.getSum() / 1000.0, kiloLuxHoursUnit), getString(R.string.solar_charging_lux_hours_total)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "+ %.0f %s", gainMinutesAccumulator.getSum(), minutesUnit), getString(R.string.solar_charging_battery_gain_total)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.1f%s", luxHoursAccumulator.getAverage() / 1000.0, kiloLuxHoursUnit), getString(R.string.solar_charging_lux_hours_avg)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "+ %.0f %s", gainMinutesAccumulator.getAverage(), minutesUnit), getString(R.string.solar_charging_battery_gain_avg)));
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        final String fmt = TOTAL_DAYS <= 7 ? "EEE" : "dd";
        final SimpleDateFormat formatDay = new SimpleDateFormat(fmt, Locale.getDefault());
        final ValueFormatter xFormatter = new ValueFormatter() {
            @Override
            public String getFormattedValue(final float value) {
                final int dayIndex = Math.round(value);
                if (dayIndex < 0 || dayIndex >= TOTAL_DAYS) {
                    return "";
                }
                final int ts = startTs + SEC_PER_DAY * dayIndex;
                return formatDay.format(new Date(ts * 1000L));
            }
        };
        chart.getXAxis().setValueFormatter(xFormatter);

        final int color = getResources().getColor(R.color.chart_solar_charging_color);
        final BarDataSet set = new BarDataSet(entries, getString(selectedMetric.labelResId));
        set.setDrawValues(false);
        set.setColor(color);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        final float yMax = set.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(yMax * 1.2f, selectedMetric.yAxisMinScale));
        chart.getAxisLeft().setAxisMinimum(0f);

        final LegendEntry legendEntry = new LegendEntry();
        legendEntry.label = getString(selectedMetric.labelResId) + " (" + selectedMetric.unit + ")";
        legendEntry.formColor = color;
        chart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        chart.getLegend().setCustom(Collections.singletonList(legendEntry));

        chart.setData(new BarData(set));
    }

    @Override
    protected void renderCharts() {
        chart.invalidate();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);

        final XAxis xAxisBottom = chart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);
        xAxisBottom.setGranularity(1f);
        xAxisBottom.setGranularityEnabled(true);
        xAxisBottom.setAxisMinimum(-0.5f);
        xAxisBottom.setAxisMaximum(TOTAL_DAYS - 0.5f);

        final YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMinimum(0f);
        yAxisLeft.setDrawTopYLabelEntry(true);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }

    protected static class SolarChargingPeriodData extends ChartsData {
        private final List<DayData> days;
        private final SolarMetric metric;

        protected SolarChargingPeriodData(final List<DayData> days, final SolarMetric metric) {
            this.days = days;
            this.metric = metric;
        }
    }

    private static class DayData {
        private final double luxHours;
        private final double gainMinutes;

        DayData(final double luxHours, final double gainMinutes) {
            this.luxHours = luxHours;
            this.gainMinutes = gainMinutes;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getSolarChargeSampleProvider(device, db.getDaoSession()));
    }
}
