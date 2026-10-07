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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class RacePredictionPeriodFragment extends AbstractChartFragment<RacePredictionPeriodFragment.RacePredictionData> {
    private static final String ARG_TOTAL_DAYS = "totalDays";
    private static final String ARG_SHOW_TILES = "showTiles";
    private static final String STATE_SELECTED_METRIC = "selectedMetric";
    private static final int DEFAULT_TOTAL_DAYS = 30;

    private static final MetricSample.Metric[] METRICS_IN_CHIP_ORDER = {
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_10K,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_HALF_MARATHON,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_FULL_MARATHON,
    };
    private static final MetricSample.Metric DEFAULT_METRIC = MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K;
    private static final float MIN_VALUE_LABEL_SPACING_DP = 32f;
    private static final float ESTIMATED_Y_AXIS_WIDTH_DP = 40f;

    private int totalDays;
    private boolean showTiles;
    private GBDevice device;
    private MetricSample.Metric selectedMetric = DEFAULT_METRIC;
    private float density;
    private final Set<Entry> labeledEntries = new HashSet<>();

    private TextView dateView;
    private LineChart raceChart;
    private ChipGroup metricChipGroup;
    private LinearLayout statsContainer;

    protected int CHART_TEXT_COLOR;
    protected int TEXT_COLOR;
    protected int LINE_COLOR;

    public static RacePredictionPeriodFragment newInstance(final int totalDays, final boolean showTiles) {
        final RacePredictionPeriodFragment fragment = new RacePredictionPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        args.putBoolean(ARG_SHOW_TILES, showTiles);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_race_predictor);
    }

    @Override
    protected boolean isSingleDay() {
        return totalDays == 1;
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    protected void init() {
        totalDays = getArguments() != null ? getArguments().getInt(ARG_TOTAL_DAYS, DEFAULT_TOTAL_DAYS) : DEFAULT_TOTAL_DAYS;
        showTiles = getArguments() != null && getArguments().getBoolean(ARG_SHOW_TILES, false);
        density = getResources().getDisplayMetrics().density;
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
        LINE_COLOR = MaterialColors.getColor(requireContext(), R.attr.accent_color, getResources().getColor(R.color.accent));
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final int layoutRes = showTiles ? R.layout.fragment_race_prediction_month : R.layout.fragment_race_prediction_period;
        final View rootView = inflater.inflate(layoutRes, container, false);

        device = getChartsHost().getDevice();
        dateView = rootView.findViewById(R.id.race_prediction_date_view);
        raceChart = rootView.findViewById(R.id.race_prediction_chart);
        metricChipGroup = rootView.findViewById(R.id.race_prediction_chip_group);

        if (showTiles) {
            statsContainer = rootView.findViewById(R.id.race_prediction_stats_container);
        }

        if (savedInstanceState != null) {
            final String savedMetricName = savedInstanceState.getString(STATE_SELECTED_METRIC);
            if (savedMetricName != null) {
                try {
                    selectedMetric = MetricSample.Metric.valueOf(savedMetricName);
                } catch (final IllegalArgumentException ignored) {
                    selectedMetric = DEFAULT_METRIC;
                }
            }
        }

        setupMetricChips(inflater);
        setupRaceChart();
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
        for (final MetricSample.Metric metric : METRICS_IN_CHIP_ORDER) {
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
            final MetricSample.Metric metric = (MetricSample.Metric) checkedChip.getTag();
            if (metric != selectedMetric) {
                selectedMetric = metric;
                refresh();
            }
        });
    }

    private void setupRaceChart() {
        raceChart.getDescription().setEnabled(false);
        raceChart.getLegend().setEnabled(false);
        raceChart.setMaxVisibleValueCount(Integer.MAX_VALUE);
        raceChart.setScaleXEnabled(true);
        raceChart.setScaleYEnabled(false);
        raceChart.setDragEnabled(true);
        raceChart.setDoubleTapToZoomEnabled(true);
        raceChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(final MotionEvent me, final ChartTouchListener.ChartGesture lastPerformedGesture) {
            }

            @Override
            public void onChartGestureEnd(final MotionEvent me, final ChartTouchListener.ChartGesture lastPerformedGesture) {
                updateValueLabelVisibility(false);
            }

            @Override
            public void onChartLongPressed(final MotionEvent me) {
            }

            @Override
            public void onChartDoubleTapped(final MotionEvent me) {
                updateValueLabelVisibility(false);
            }

            @Override
            public void onChartSingleTapped(final MotionEvent me) {
            }

            @Override
            public void onChartFling(final MotionEvent me1, final MotionEvent me2, final float velocityX, final float velocityY) {
            }

            @Override
            public void onChartScale(final MotionEvent me, final float scaleX, final float scaleY) {
                updateValueLabelVisibility(false);
            }

            @Override
            public void onChartTranslate(final MotionEvent me, final float dX, final float dY) {
                updateValueLabelVisibility(false);
            }
        });

        final XAxis xAxis = raceChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(CHART_TEXT_COLOR);
        xAxis.setGranularity(1f);

        final YAxis yAxisLeft = raceChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(final float value) {
                return formatSeconds(value);
            }
        });

        final YAxis yAxisRight = raceChart.getAxisRight();
        yAxisRight.setEnabled(false);
    }

    @Override
    protected RacePredictionData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final Calendar day = Calendar.getInstance();
        day.setTime(DateTimeUtils.dayStart(new Date(getTSEnd() * 1000L)));
        day.add(Calendar.DATE, -totalDays + 1);
        final long windowStartMillis = day.getTimeInMillis();

        final List<RacePredictionDay> days = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            final long dayStartMillis = day.getTimeInMillis();
            final long dayEndMillis = DateTimeUtils.dayEnd(day.getTime()).getTime();
            Double value = null;
            final MetricSample sample = GenericMetricSampleProvider.getLatestMetricSample(db, device, selectedMetric, dayStartMillis, dayEndMillis);
            if (sample != null) {
                value = sample.getMetricScore();
            }
            days.add(new RacePredictionDay((Calendar) day.clone(), value, i));
            day.add(Calendar.DATE, 1);
        }

        if (days.get(0).value == null) {
            final MetricSample carryIn = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, selectedMetric, windowStartMillis);
            if (carryIn != null) {
                days.set(0, new RacePredictionDay(days.get(0).day, carryIn.getMetricScore(), 0));
            }
        }

        Double[] latestValues = null;
        Double[] trendDeltas = null;
        if (showTiles) {
            final long asOfMillis = DateTimeUtils.dayEnd(new Date(getTSEnd() * 1000L)).getTime();
            latestValues = new Double[METRICS_IN_CHIP_ORDER.length];
            trendDeltas = new Double[METRICS_IN_CHIP_ORDER.length];
            for (int i = 0; i < METRICS_IN_CHIP_ORDER.length; i++) {
                final MetricSample latest = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, METRICS_IN_CHIP_ORDER[i], asOfMillis);
                latestValues[i] = latest != null ? latest.getMetricScore() : null;
                if (latest != null) {
                    final MetricSample baseline = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, METRICS_IN_CHIP_ORDER[i], windowStartMillis);
                    if (baseline != null) {
                        final double delta = latest.getMetricScore() - baseline.getMetricScore();
                        trendDeltas[i] = delta != 0 ? delta : null;
                    }
                }
            }
        }

        return new RacePredictionData(days, latestValues, trendDeltas, selectedMetric);
    }

    @Override
    protected void updateChartsnUIThread(final RacePredictionData data) {
        if (data.metric != selectedMetric) {
            // A chip was tapped again while a previous refresh for the old metric was still in flight.
            return;
        }

        dateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final List<Entry> entries = new ArrayList<>();
        for (final RacePredictionDay raceDay : data.days) {
            if (raceDay.value != null) {
                entries.add(new Entry(raceDay.i, raceDay.value.floatValue()));
            }
        }

        final LineDataSet dataSet = createDataSet(entries, data.metric);
        final LineData lineData = new LineData(dataSet);
        raceChart.getXAxis().setValueFormatter(getDayValueFormatter(data));
        raceChart.getXAxis().setAxisMinimum(0f);
        raceChart.getXAxis().setAxisMaximum(totalDays - 1);

        if (!entries.isEmpty()) {
            float yMin = Float.MAX_VALUE;
            float yMax = -Float.MAX_VALUE;
            for (final Entry entry : entries) {
                yMin = Math.min(yMin, entry.getY());
                yMax = Math.max(yMax, entry.getY());
            }
            final float padding = Math.max(60f, (yMax - yMin) * 0.1f);
            raceChart.getAxisLeft().setAxisMinimum(Math.max(0f, yMin - padding));
            raceChart.getAxisLeft().setAxisMaximum(yMax + padding);
        } else {
            raceChart.getAxisLeft().setAxisMinimum(0f);
            raceChart.getAxisLeft().setAxisMaximum(1f);
        }

        raceChart.setData(lineData);
        updateValueLabelVisibility(true);

        if (showTiles) {
            updateTiles(data.latestValues, data.trendDeltas);
        }
    }

    private void updateTiles(@Nullable final Double[] latestValues, @Nullable final Double[] trendDeltas) {
        if (latestValues == null) {
            return;
        }

        final List<StatTileData> stats = new ArrayList<>();
        for (int i = 0; i < METRICS_IN_CHIP_ORDER.length; i++) {
            stats.add(buildStatTile(latestValues[i], trendDeltas != null ? trendDeltas[i] : null, METRICS_IN_CHIP_ORDER[i]));
        }

        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);
    }

    private StatTileData buildStatTile(@Nullable final Double value, @Nullable final Double deltaSeconds, final MetricSample.Metric metric) {
        final String tileValue = value != null ? formatSeconds(value) : getString(R.string.stats_empty_value);
        final String label = getString(metric.labelResId);

        if (deltaSeconds == null) {
            return new StatTileData(tileValue, label);
        }

        final boolean faster = deltaSeconds < 0;
        final int iconRes = faster ? R.drawable.ic_caret_down_solid : R.drawable.ic_caret_up_solid;
        final int color = ContextCompat.getColor(requireContext(), faster ? R.color.body_energy_level_color : R.color.body_energy_lost_color);

        return new StatTileData(tileValue, label, null, formatSeconds(Math.abs(deltaSeconds)), iconRes, color);
    }

    private LineDataSet createDataSet(final List<Entry> entries, final MetricSample.Metric metric) {
        final LineDataSet dataSet = new LineDataSet(entries, getString(metric.labelResId));
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setColor(LINE_COLOR);
        dataSet.setCircleColor(LINE_COLOR);
        dataSet.setDrawCircleHole(false);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircles(true);
        dataSet.setLineWidth(2f);
        dataSet.setValueTextColor(TEXT_COLOR);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawValues(true);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPointLabel(final Entry entry) {
                return labeledEntries.contains(entry) ? formatSeconds(entry.getY()) : "";
            }
        });
        return dataSet;
    }

    private void updateValueLabelVisibility(final boolean justLoadedData) {
        labeledEntries.clear();

        final LineData lineData = raceChart.getData();
        if (lineData == null) {
            raceChart.invalidate();
            return;
        }

        final float lowestVisibleX;
        final float highestVisibleX;
        if (justLoadedData) {
            lowestVisibleX = raceChart.getXAxis().getAxisMinimum();
            highestVisibleX = raceChart.getXAxis().getAxisMaximum();
        } else {
            lowestVisibleX = raceChart.getLowestVisibleX();
            highestVisibleX = raceChart.getHighestVisibleX();
        }

        float contentWidthPx = raceChart.getViewPortHandler().contentWidth();
        if (contentWidthPx <= 0) {
            // The view itself may also not have been laid out yet on a first load.
            contentWidthPx = getResources().getDisplayMetrics().widthPixels - ESTIMATED_Y_AXIS_WIDTH_DP * density;
        }

        final List<Entry> visibleSorted = new ArrayList<>();
        for (final ILineDataSet dataSet : lineData.getDataSets()) {
            for (int i = 0; i < dataSet.getEntryCount(); i++) {
                final Entry entry = dataSet.getEntryForIndex(i);
                if (entry.getX() >= lowestVisibleX && entry.getX() <= highestVisibleX) {
                    visibleSorted.add(entry);
                }
            }
        }
        if (visibleSorted.isEmpty()) {
            raceChart.invalidate();
            return;
        }
        Collections.sort(visibleSorted, (a, b) -> Float.compare(a.getX(), b.getX()));

        final int maxLabelSlots = contentWidthPx > 0
                ? Math.max(2, (int) (contentWidthPx / (MIN_VALUE_LABEL_SPACING_DP * density)))
                : visibleSorted.size();

        if (visibleSorted.size() <= maxLabelSlots) {
            labeledEntries.addAll(visibleSorted);
        } else {
            final int lastIndex = visibleSorted.size() - 1;
            for (int slot = 0; slot < maxLabelSlots; slot++) {
                final int index = Math.round(slot * (float) lastIndex / (maxLabelSlots - 1));
                labeledEntries.add(visibleSorted.get(index));
            }
        }

        raceChart.invalidate();
    }

    private static String formatSeconds(final double seconds) {
        final long totalSeconds = Math.round(seconds);
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }

    private ValueFormatter getDayValueFormatter(final RacePredictionData data) {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(final float value) {
                final RacePredictionDay raceDay = data.getDay((int) value);
                final String pattern = totalDays > 7 ? "dd/MM" : "EEE";
                final SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
                return format.format(new Date(raceDay.day.getTimeInMillis()));
            }
        };
    }

    @Override
    protected void renderCharts() {
        raceChart.invalidate();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
        // Legend is disabled — only one metric's line is ever shown, selected via the chips.
    }

    protected static class RacePredictionDay {
        final Calendar day;
        @Nullable
        final Double value;
        final int i;

        RacePredictionDay(final Calendar day, @Nullable final Double value, final int i) {
            this.day = day;
            this.value = value;
            this.i = i;
        }
    }

    protected static class RacePredictionData extends ChartsData {
        final List<RacePredictionDay> days;
        @Nullable
        final Double[] latestValues;
        @Nullable
        final Double[] trendDeltas;
        final MetricSample.Metric metric;

        RacePredictionData(final List<RacePredictionDay> days, @Nullable final Double[] latestValues, @Nullable final Double[] trendDeltas, final MetricSample.Metric metric) {
            this.days = days;
            this.latestValues = latestValues;
            this.trendDeltas = trendDeltas;
            this.metric = metric;
        }

        RacePredictionDay getDay(final int i) {
            return days.get(i);
        }
    }
}
