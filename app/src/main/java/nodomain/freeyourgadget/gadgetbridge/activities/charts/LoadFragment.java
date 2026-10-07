/*  Copyright (C) 2025-2026 a0z, Thomas Kuehne

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

import static nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider.getLatestMetricSampleBefore;
import static nodomain.freeyourgadget.gadgetbridge.model.MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE;
import static nodomain.freeyourgadget.gadgetbridge.model.MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.SectionHeaderView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.model.TrainingLoadStatus;
import nodomain.freeyourgadget.gadgetbridge.model.WorkoutLoadSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;


public class LoadFragment extends AbstractChartFragment<LoadFragment.LoadsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(LoadFragment.class);
    protected final int TOTAL_DAYS = 30;
    protected static final float OPTIMAL_LOAD_RATIO_LOWER = 0.8f;
    protected static final float OPTIMAL_LOAD_RATIO_UPPER = 1.5f;

    private enum LoadDataType {
        ACUTE_LOAD,
        CHRONIC_LOAD
    }

    private static final LoadDataType[] LOAD_DATA_TYPE_ORDER = {
            LoadDataType.ACUTE_LOAD,
            LoadDataType.CHRONIC_LOAD
    };
    private static final LoadDataType DEFAULT_LOAD_DATA_TYPE = LoadDataType.ACUTE_LOAD;

    protected GaugeDrawer gaugeDrawer;
    private ImageView acuteLoadRatioGauge;
    private TextView acuteLoadRatioGaugeValue;
    private TextView acuteLoadRatioGaugeStatus;

    private LinearLayout acuteChronicLoadStatsContainer;
    private LinearLayout weeklyLoadStatsContainer;
    private TextView dateHeader;
    private CombinedChart acuteLoadChart;
    private BarChart dailyLoadChart;
    private ChipGroup loadChartDataTypeGroup;
    protected int CHART_TEXT_COLOR;
    protected int LEGEND_TEXT_COLOR;
    protected int TEXT_COLOR;
    protected int LOAD_COLOR;
    protected int OPTIMAL_LOAD_FILL_COLOR;

    private boolean showAcuteLoad = true;
    private boolean showChronicLoad = true;

    private boolean metricTrainingLoad;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_load, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        dateHeader = rootView.findViewById(R.id.date_view);
        dailyLoadChart = rootView.findViewById(R.id.daily_load_chart);
        weeklyLoadStatsContainer = rootView.findViewById(R.id.weekly_load_stats_container);

        metricTrainingLoad = GBApplication.getPrefs().experimentalMetrics()
                && supportsMetrics(GENERIC_TRAINING_LOAD_ACUTE);
        if (metricTrainingLoad) {
            LOG.info("using experimental MetricSample for training load");
        }

        if (supportsTrainingLoad()) {
            acuteLoadChart = rootView.findViewById(R.id.acute_load_chart);
            acuteChronicLoadStatsContainer = rootView.findViewById(R.id.acute_chronic_load_stats_container);
            acuteLoadRatioGauge = rootView.findViewById(R.id.acute_load_ratio_gauge);
            acuteLoadRatioGaugeValue = rootView.findViewById(R.id.acute_load_ratio_gauge_value);
            acuteLoadRatioGaugeStatus = rootView.findViewById(R.id.acute_load_ratio_gauge_status);
            loadChartDataTypeGroup = rootView.findViewById(R.id.load_chart_data_type_group);
            gaugeDrawer = new GaugeDrawer();
            showChronicLoad = supportsTrainingLoadChronic();
            if (!showChronicLoad) {
                ((SectionHeaderView) rootView.findViewById(R.id.acute_load_header)).setTitle(getString(R.string.pref_header_training_load));
            }
            setupLoadDataTypeChips(inflater);
            setupAcuteLoadChart();
        } else {
            rootView.findViewById(R.id.training_load_wrapper).setVisibility(View.GONE);
        }
        setupDailyLoadChart();
        refresh();

        return rootView;
    }

    public boolean supportsTrainingLoad() {
        if (metricTrainingLoad) {
            return true;
        }
        final GBDevice device = getChartsHost().getDevice();
        return device.getDeviceCoordinator().supportsTrainingLoad(device);
    }

    public boolean supportsTrainingLoadChronic() {
        final GBDevice device = getChartsHost().getDevice();
        return device.getDeviceCoordinator().supportsTrainingLoadChronic(device);
    }

    @Override
    public String getTitle() {
        return getString(R.string.pref_header_training_load);
    }

    @Override
    protected void init() {
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        LEGEND_TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
        LOAD_COLOR = getAcuteColor(requireContext());
        OPTIMAL_LOAD_FILL_COLOR = getResources().getColor(R.color.training_load_optimal_fill_color);
    }

    @Override
    protected LoadsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());

        return getData(db, day, device);
    }

    @Override
    protected void renderCharts() {
        if (acuteLoadChart != null) {
            acuteLoadChart.invalidate();
        }
        dailyLoadChart.invalidate();
    }

    protected LineDataSet createDataSet(final List<Entry> values, String name, int color) {
        final LineDataSet lineDataSet = new LineDataSet(values, name);
        lineDataSet.setColor(color);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setLineWidth(2f);
        lineDataSet.setFillAlpha(255);
        lineDataSet.setCircleRadius(5f);
        lineDataSet.setDrawCircles(true);
        lineDataSet.setDrawCircleHole(false);
        lineDataSet.setCircleHoleColor(Color.WHITE);
        lineDataSet.setCircleColor(color);
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawValues(false);
        return lineDataSet;
    }

    protected BarDataSet createOptimalLoadRangeDataSet(final List<BarEntry> values) {
        final BarDataSet barDataSet = new BarDataSet(values, getString(R.string.training_optimal_load));
        barDataSet.setDrawValues(false);
        barDataSet.setDrawIcons(false);
        barDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        barDataSet.setColors(0x00000000, OPTIMAL_LOAD_FILL_COLOR);
        barDataSet.setHighLightAlpha(0);
        barDataSet.setHighlightEnabled(false);
        return barDataSet;
    }

    private void setupLoadDataTypeChips(final LayoutInflater inflater) {
        if (loadChartDataTypeGroup == null) {
            return;
        }

        loadChartDataTypeGroup.removeAllViews();
        loadChartDataTypeGroup.setSingleSelection(false);
        loadChartDataTypeGroup.setSelectionRequired(true);
        for (final LoadDataType dataType : LOAD_DATA_TYPE_ORDER) {
            if (isLoadDataTypeSupported(dataType)) {
                addLoadDataTypeChip(inflater, dataType);
            }
        }
        loadChartDataTypeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.charts_at_least_one_item, Toast.LENGTH_SHORT).show();
                final Chip defaultChip = findLoadDataTypeChip(DEFAULT_LOAD_DATA_TYPE);
                if (defaultChip != null) {
                    defaultChip.setChecked(true);
                }
                return;
            }

            final boolean selectedAcuteLoad = isLoadDataTypeSelected(LoadDataType.ACUTE_LOAD);
            final boolean selectedChronicLoad = isLoadDataTypeSelected(LoadDataType.CHRONIC_LOAD);
            if (showAcuteLoad == selectedAcuteLoad && showChronicLoad == selectedChronicLoad) {
                return;
            }

            showAcuteLoad = selectedAcuteLoad;
            showChronicLoad = selectedChronicLoad;
            refresh();
        });
    }

    private void addLoadDataTypeChip(final LayoutInflater inflater, final LoadDataType dataType) {
        final Chip chip = (Chip) inflater.inflate(R.layout.layout_chart_chip, loadChartDataTypeGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(getString(getLoadDataTypeLabel(dataType)));
        chip.setTag(dataType);
        loadChartDataTypeGroup.addView(chip);
        chip.setChecked(isLoadDataTypeVisible(dataType));
    }

    private boolean isLoadDataTypeSupported(final LoadDataType dataType) {
        if (dataType == LoadDataType.CHRONIC_LOAD) {
            final GBDevice device = getChartsHost().getDevice();
            return device.getDeviceCoordinator().supportsTrainingLoadChronic(device);
        }
        return true;
    }

    private boolean isLoadDataTypeSelected(final LoadDataType dataType) {
        final Chip chip = findLoadDataTypeChip(dataType);
        return chip != null && chip.isChecked();
    }

    private boolean isLoadDataTypeVisible(final LoadDataType dataType) {
        switch (dataType) {
            case ACUTE_LOAD:
                return showAcuteLoad;
            case CHRONIC_LOAD:
                return showChronicLoad;
            default:
                throw new IllegalArgumentException("Unknown load data type: " + dataType);
        }
    }

    private Chip findLoadDataTypeChip(final LoadDataType dataType) {
        for (int i = 0; i < loadChartDataTypeGroup.getChildCount(); i++) {
            final View child = loadChartDataTypeGroup.getChildAt(i);
            if (child instanceof Chip && child.getTag() == dataType) {
                return (Chip) child;
            }
        }
        return null;
    }

    private int getLoadDataTypeLabel(final LoadDataType dataType) {
        switch (dataType) {
            case ACUTE_LOAD:
                return R.string.training_acute_load;
            case CHRONIC_LOAD:
                return R.string.training_chronic_load;
            default:
                throw new IllegalArgumentException("Unknown load data type: " + dataType);
        }
    }

    @Override
    protected void updateChartsnUIThread(LoadsData data) {
        if (data == null) {
            return;
        }
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(getEndDate());
        dateHeader.setText(formattedDate);
        weeklyLoadStatsContainer.removeAllViews();
        final List<StatTileData> weeklyLoadStats = new ArrayList<>();
        weeklyLoadStats.add(new StatTileData(String.valueOf(data.getThisWeekLoad()), getString(R.string.this_week_total)));
        weeklyLoadStats.add(new StatTileData(String.valueOf(data.getLastWeekLoad()), getString(R.string.last_week_total)));
        StatTileGridUtilKt.addStatTileGrid(weeklyLoadStatsContainer, requireContext(), weeklyLoadStats, 0);
        dailyLoadChart.setData(null);
        acuteLoadChart.setData(null);

        List<Entry> acuteLoadEntries = new ArrayList<>();
        List<Entry> chronicLoadEntries = new ArrayList<>();
        List<BarEntry> dailyLoadEntries = new ArrayList<>();
        List<BarEntry> optimalLoadEntries = new ArrayList<>();
        data.getData().forEach((LoadData dayData) -> {
            if (dayData.acuteLoad > 0) {
                acuteLoadEntries.add(new Entry(dayData.i, dayData.acuteLoad));
            }
            if (dayData.chronicLoad > 0) {
                chronicLoadEntries.add(new Entry(dayData.i, dayData.chronicLoad));
                float optimalLower = dayData.chronicLoad * OPTIMAL_LOAD_RATIO_LOWER;
                float optimalUpper = dayData.chronicLoad * OPTIMAL_LOAD_RATIO_UPPER;
                optimalLoadEntries.add(new BarEntry(dayData.i, new float[]{optimalLower, optimalUpper - optimalLower}));
            }
            if (dayData.load > 0) {
                dailyLoadEntries.add(new BarEntry(dayData.i, dayData.load));
            }
        });

        // Daily load chart.
        BarDataSet set = new BarDataSet(dailyLoadEntries, getString(R.string.training_daily_load));
        set.setDrawValues(true);
        set.setColors(LOAD_COLOR);
        final XAxis x = dailyLoadChart.getXAxis();
        x.setValueFormatter(getDailyLoadChartDayValueFormatter(data));
        dailyLoadChart.getAxisLeft().setAxisMaximum(Math.max(set.getYMax(), 200) + 100);
        dailyLoadChart.setRenderer(new AngledLabelsChartRenderer(dailyLoadChart, dailyLoadChart.getAnimator(), dailyLoadChart.getViewPortHandler()));
        BarData barData = new BarData(set);
        barData.setValueTextColor(TEXT_COLOR);
        barData.setValueTextSize(10f);

        LegendEntry dailyLoadEntry = new LegendEntry();
        dailyLoadEntry.label = getString(R.string.training_daily_load);
        dailyLoadEntry.formColor = LOAD_COLOR;
        dailyLoadEntry.form = Legend.LegendForm.SQUARE;
        dailyLoadChart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        dailyLoadChart.getLegend().setCustom(Collections.singletonList(dailyLoadEntry));

        dailyLoadChart.setData(barData);

        if (supportsTrainingLoad()) {
            List<LegendEntry> legendEntries = new ArrayList<>(3);
            if (showAcuteLoad) {
                LegendEntry acuteLoadEntry = new LegendEntry();
                acuteLoadEntry.label = getString(R.string.training_acute_load);
                acuteLoadEntry.formColor = LOAD_COLOR;
                acuteLoadEntry.form = Legend.LegendForm.CIRCLE;
                legendEntries.add(acuteLoadEntry);
            }
            if (showChronicLoad) {
                LegendEntry chronicLoadEntry = new LegendEntry();
                chronicLoadEntry.label = getString(R.string.training_chronic_load);
                chronicLoadEntry.formColor = getResources().getColor(R.color.training_chronic_load);
                chronicLoadEntry.form = Legend.LegendForm.CIRCLE;
                legendEntries.add(chronicLoadEntry);
            }
            if (showChronicLoad && !optimalLoadEntries.isEmpty()) {
                LegendEntry optimalLoadEntry = new LegendEntry();
                optimalLoadEntry.label = getString(R.string.training_optimal_load);
                optimalLoadEntry.formColor = OPTIMAL_LOAD_FILL_COLOR;
                optimalLoadEntry.form = Legend.LegendForm.SQUARE;
                legendEntries.add(optimalLoadEntry);
            }
            acuteLoadChart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
            acuteLoadChart.getLegend().setCustom(legendEntries);
            acuteLoadChart.getXAxis().setValueFormatter(getDailyLoadChartDayValueFormatter(data));

            final List<ILineDataSet> lineDataSets = new ArrayList<>();
            if (showAcuteLoad) {
                lineDataSets.add(createDataSet(acuteLoadEntries, getString(R.string.training_acute_load), LOAD_COLOR));
            }
            if (showChronicLoad) {
                lineDataSets.add(createDataSet(chronicLoadEntries, getString(R.string.training_chronic_load), getResources().getColor(R.color.training_chronic_load)));
            }
            final LineData lineData = new LineData(lineDataSets);

            final CombinedData combinedData = new CombinedData();
            float optimalLoadMax = 0f;
            if (showChronicLoad && !optimalLoadEntries.isEmpty()) {
                final BarData optimalLoadBarData = new BarData(createOptimalLoadRangeDataSet(optimalLoadEntries));
                optimalLoadBarData.setBarWidth(1f);
                combinedData.setData(optimalLoadBarData);
                optimalLoadMax = optimalLoadBarData.getYMax();
            }
            combinedData.setData(lineData);
            acuteLoadChart.getAxisLeft().setAxisMaximum(Math.max(Math.max(lineData.getYMax(), optimalLoadMax), 200) + 100);
            acuteLoadChart.setData(combinedData);

            // Acute load ratio gauge
            int latestAcuteLoad = data.getLatestAcuteLoad();
            int latestChronicLoad = data.getLatestChronicLoad();
            acuteChronicLoadStatsContainer.removeAllViews();
            final List<StatTileData> acuteChronicLoadStats = new ArrayList<>();
            acuteChronicLoadStats.add(new StatTileData(
                    String.valueOf(latestAcuteLoad),
                    showChronicLoad ? getString(R.string.training_acute_load) : getString(R.string.pref_header_training_load)
            ));
            if (showChronicLoad) {
                acuteChronicLoadStats.add(new StatTileData(String.valueOf(latestChronicLoad), getString(R.string.training_chronic_load)));
            }
            StatTileGridUtilKt.addStatTileGrid(acuteChronicLoadStatsContainer, requireContext(), acuteChronicLoadStats, 0);
            // Gauge
            acuteLoadRatioGaugeValue.setText(String.valueOf(latestAcuteLoad));
            float value;
            final TrainingLoadStatus reportedStatus = data.getReportedStatus();
            if (reportedStatus != null) {
                // The device names its own zone, so the needle only has to land inside the matching
                // gauge segment rather than encode a ratio.
                switch (reportedStatus) {
                    case LOW:
                        value = 0.166f;
                        acuteLoadRatioGaugeStatus.setText(getString(R.string.low));
                        break;
                    case OPTIMAL:
                        value = 0.5f;
                        acuteLoadRatioGaugeStatus.setText(getString(R.string.optimal));
                        break;
                    case HIGH:
                        value = 0.833f;
                        acuteLoadRatioGaugeStatus.setText(getString(R.string.high));
                        break;
                    default:
                        value = 1f;
                        acuteLoadRatioGaugeStatus.setText(getString(R.string.very_high));
                        break;
                }
            } else if (latestAcuteLoad > 0 && latestChronicLoad > 0) {
                value = (float) latestAcuteLoad / latestChronicLoad;
                if (value < OPTIMAL_LOAD_RATIO_LOWER) {
                    value = (float) GaugeDrawer.normalize(value, 0, OPTIMAL_LOAD_RATIO_LOWER, 0, 0.333);
                    acuteLoadRatioGaugeStatus.setText(getString(R.string.low));
                } else if (value < OPTIMAL_LOAD_RATIO_UPPER) {
                    value = (float) GaugeDrawer.normalize(value, OPTIMAL_LOAD_RATIO_LOWER, OPTIMAL_LOAD_RATIO_UPPER, 0.334f, 0.666);
                    acuteLoadRatioGaugeStatus.setText(getString(R.string.optimal));
                } else if (value < 2) {
                    value = (float) GaugeDrawer.normalize(value, OPTIMAL_LOAD_RATIO_UPPER, 2, 0.667f, 1);
                    acuteLoadRatioGaugeStatus.setText(getString(R.string.high));
                } else {
                    value = 1;
                    acuteLoadRatioGaugeStatus.setText(getString(R.string.very_high));
                }
            } else {
                value = 0;
                acuteLoadRatioGaugeStatus.setText(getString(R.string.none));
            }

            int[] colors = new int[]{
                    ContextCompat.getColor(GBApplication.getContext(), R.color.training_load_low),
                    ContextCompat.getColor(GBApplication.getContext(), R.color.training_load_optimal),
                    ContextCompat.getColor(GBApplication.getContext(), R.color.training_load_high),
            };
            float[] segments = new float[]{
                    0.333f, // low
                    0.333f, // optimal
                    0.333f, // high
            };
            gaugeDrawer.drawSegmentedGauge(acuteLoadRatioGauge, colors, segments, value, false, true);
        }
    }

    private LoadsData getData(DBHandler db, Calendar day, GBDevice device) {
        final boolean chronicLoadSupported = supportsTrainingLoadChronic();
        final ZonedDateTime now = day.toInstant().atZone(ZoneId.systemDefault());
        final ZonedDateTime startOfThisWeek = now.with(DayOfWeek.MONDAY).with(LocalTime.of(0, 0, 0));
        final ZonedDateTime endOfThisWeek = startOfThisWeek.plusDays(6).with(LocalTime.of(23, 59, 59));
        final ZonedDateTime startOfLastWeek = now.minusWeeks(1).with(DayOfWeek.MONDAY).with(LocalTime.of(0, 0, 0));
        final ZonedDateTime endOfLastWeek = startOfLastWeek.plusDays(6).with(LocalTime.of(23, 59, 59));
        day = DateTimeUtils.dayStart(day);
        day.add(Calendar.DATE, -TOTAL_DAYS + 1);
        List<LoadData> data = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            int startTs = (int) (day.getTimeInMillis() / 1000);
            int endTs = startTs + 24 * 60 * 60 - 1;
            List<? extends WorkoutLoadSample> workoutLoadSamples = getWorkoutLoadSamples(db, device, startTs, endTs);
            int load = 0;
            int acuteLoad = 0;
            int chronicLoad = 0;
            if (!workoutLoadSamples.isEmpty()) {
                load = workoutLoadSamples.stream().mapToInt(WorkoutLoadSample::getValue).sum();
            }
            if (supportsTrainingLoad()) {
                final long dayStartMillis = startTs * 1000L;
                final long dayEndMillis = DateTimeUtils.dayEnd(day.getTime()).getTime();
                if (metricTrainingLoad) {
                    MetricSample dayAcuteLoadSample = getLatestMetricSampleBefore(db, device, GENERIC_TRAINING_LOAD_ACUTE, dayEndMillis);
                    if (dayAcuteLoadSample != null && dayAcuteLoadSample.getTimestamp() >= dayStartMillis) {
                        acuteLoad = (int) dayAcuteLoadSample.getMetricScore();
                    }
                    if (chronicLoadSupported) {
                        MetricSample dayChronicLoadSample = getLatestMetricSampleBefore(db, device, GENERIC_TRAINING_LOAD_CHRONIC, dayEndMillis);
                        if (dayChronicLoadSample != null && dayChronicLoadSample.getTimestamp() >= dayStartMillis) {
                            chronicLoad = (int) dayChronicLoadSample.getMetricScore();
                        }
                    }
                } else {
                    GenericTrainingLoadAcuteSample dayAcuteLoadSample = getLatestTrainingLoadAcuteSample(db, device, dayEndMillis);
                    if (dayAcuteLoadSample != null && dayAcuteLoadSample.getTimestamp() >= dayStartMillis) {
                        acuteLoad = dayAcuteLoadSample.getValue();
                    }
                    if (chronicLoadSupported) {
                        GenericTrainingLoadChronicSample dayChronicLoadSample = getLatestTrainingLoadChronicSample(db, device, dayEndMillis);
                        if (dayChronicLoadSample != null && dayChronicLoadSample.getTimestamp() >= dayStartMillis) {
                            chronicLoad = dayChronicLoadSample.getValue();
                        }
                    }
                }
            }
            data.add(new LoadData((Calendar) day.clone(), load, acuteLoad, chronicLoad, i));
            day.add(Calendar.DATE, 1);
        }
        int thisWeekLoad = getWorkoutLoadSamples(db, device, (int) (startOfThisWeek.toInstant().toEpochMilli() / 1000), (int) (endOfThisWeek.toInstant().toEpochMilli() / 1000))
                .stream()
                .mapToInt(WorkoutLoadSample::getValue)
                .sum();
        int lastWeekLoad = getWorkoutLoadSamples(db, device, (int) (startOfLastWeek.toInstant().toEpochMilli() / 1000), (int) (endOfLastWeek.toInstant().toEpochMilli() / 1000))
                .stream()
                .mapToInt(WorkoutLoadSample::getValue)
                .sum();

        int latestAcuteLoad = 0;
        int latestChronicLoad = 0;
        TrainingLoadStatus reportedStatus = null;
        if (supportsTrainingLoad()) {
            final Date dayEnd = DateTimeUtils.dayEnd(getEndDate());
            reportedStatus = device.getDeviceCoordinator().getTrainingLoadStatus(device, db.getDaoSession(), dayEnd.getTime());

            if (metricTrainingLoad) {
                MetricSample latestAcuteLoadSample = getLatestMetricSampleBefore(db, device, GENERIC_TRAINING_LOAD_ACUTE, dayEnd.getTime());
                if (latestAcuteLoadSample != null) {
                    latestAcuteLoad = (int) latestAcuteLoadSample.getMetricScore();
                }

                if (chronicLoadSupported) {
                    MetricSample latestChronicLoadSample = getLatestMetricSampleBefore(db, device, GENERIC_TRAINING_LOAD_CHRONIC, dayEnd.getTime());
                    if (latestChronicLoadSample != null) {
                        latestChronicLoad = (int) latestChronicLoadSample.getMetricScore();
                    }
                }
            } else {
                GenericTrainingLoadAcuteSample latestAcuteLoadSample = getLatestTrainingLoadAcuteSample(db, device, dayEnd.getTime());
                if (latestAcuteLoadSample != null) {
                    latestAcuteLoad = latestAcuteLoadSample.getValue();
                }

                if (chronicLoadSupported) {
                    GenericTrainingLoadChronicSample latestChronicLoadSample = getLatestTrainingLoadChronicSample(db, device, dayEnd.getTime());
                    if (latestChronicLoadSample != null) {
                        latestChronicLoad = latestChronicLoadSample.getValue();
                    }
                }
            }
        }
        return new LoadsData(data, latestAcuteLoad, latestChronicLoad, thisWeekLoad, lastWeekLoad, reportedStatus);
    }

    private List<? extends WorkoutLoadSample> getWorkoutLoadSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends WorkoutLoadSample> sampleProvider = coordinator.getWorkoutLoadSampleProvider(device, db.getDaoSession());
        if (sampleProvider == null) {
            LOG.warn("Device {} does not implement WorkoutLoadSampleProvider", device);
            return new ArrayList<>();
        }
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    private GenericTrainingLoadAcuteSample getLatestTrainingLoadAcuteSample(final DBHandler db, final GBDevice device, long tsToMillis) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends GenericTrainingLoadAcuteSample> sampleProvider = coordinator.getTrainingAcuteLoadSampleProvider(device, db.getDaoSession());
        if (sampleProvider == null) {
            LOG.warn("Device {} does not implement GenericTrainingLoadAcuteSample", device);
            return null;
        }
        return sampleProvider.getLatestSample(tsToMillis);
    }

    private GenericTrainingLoadChronicSample getLatestTrainingLoadChronicSample(final DBHandler db, final GBDevice device, long tsToMillis) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends GenericTrainingLoadChronicSample> sampleProvider = coordinator.getTrainingChronicLoadSampleProvider(device, db.getDaoSession());
        if (sampleProvider == null) {
            LOG.warn("Device {} does not implement GenericTrainingLoadChronicSample", device);
            return null;
        }
        return sampleProvider.getLatestSample(tsToMillis);
    }

    private void setupAcuteLoadChart() {
        acuteLoadChart.getDescription().setEnabled(false);
        acuteLoadChart.setTouchEnabled(false);
        acuteLoadChart.setPinchZoom(false);
        acuteLoadChart.setDoubleTapToZoomEnabled(false);
        acuteLoadChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.LINE
        });

        final XAxis xAxisBottom = acuteLoadChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setAxisMaximum(29 + 0.5f);
        xAxisBottom.setAxisMinimum(0 - 0.5f);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisLeft = acuteLoadChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMaximum(1000);
        yAxisLeft.setAxisMinimum(0);
        yAxisLeft.setDrawTopYLabelEntry(false);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisRight = acuteLoadChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }

    protected void setupDailyLoadChart() {
        dailyLoadChart.getDescription().setEnabled(false);
        dailyLoadChart.setDoubleTapToZoomEnabled(false);
        dailyLoadChart.setTouchEnabled(false);

        final XAxis xAxisBottom = dailyLoadChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);
        xAxisBottom.setAxisMaximum(29 + 0.5f);
        xAxisBottom.setAxisMinimum(0 - 0.5f);

        final YAxis yAxisLeft = dailyLoadChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setDrawTopYLabelEntry(true);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);
        yAxisLeft.setAxisMinimum(0f);

        final YAxis yAxisRight = dailyLoadChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }

    ValueFormatter getDailyLoadChartDayValueFormatter(LoadFragment.LoadsData data) {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                LoadFragment.LoadData day = data.getDay((int) value);
                String pattern = TOTAL_DAYS > 7 ? "dd" : "EEE";
                SimpleDateFormat formatLetterDay = new SimpleDateFormat(pattern, Locale.getDefault());
                return formatLetterDay.format(new Date(day.day.getTimeInMillis()));
            }
        };
    }

    public static int getAcuteColor(Context context) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.training_acute_load, typedValue, true);
        return typedValue.data;
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    protected static class LoadsData extends ChartsData {

        private int latestAcuteLoad;
        private int latestChronicLoad;
        private int thisWeekLoad;
        private int lastWeekLoad;
        private final TrainingLoadStatus reportedStatus;

        private final List<LoadFragment.LoadData> data;

        public LoadsData(final List<LoadFragment.LoadData> chartsData, int latestAcuteLoad, int latestChronicLoad, int thisWeekLoad, int lastWeekLoad, final TrainingLoadStatus reportedStatus) {
            this.data = chartsData;
            this.latestAcuteLoad = latestAcuteLoad;
            this.latestChronicLoad = latestChronicLoad;
            this.thisWeekLoad = thisWeekLoad;
            this.lastWeekLoad = lastWeekLoad;
            this.reportedStatus = reportedStatus;
        }

        /**
         * The status the device reported itself, or {@code null} when it reports none.
         */
        public TrainingLoadStatus getReportedStatus() {
            return reportedStatus;
        }

        public LoadFragment.LoadData getDay(int i) {
            return this.data.get(i);
        }

        public LoadFragment.LoadData getCurrentDay() {
            return this.data.get(this.data.size() - 1);
        }

        public List<LoadFragment.LoadData> getData() {
            return data;
        }

        public int getLatestAcuteLoad() {
            return latestAcuteLoad;
        }

        public int getLatestChronicLoad () {
            return latestChronicLoad;
        }

        public int getLastWeekLoad() {
            return lastWeekLoad;
        }

        public int getThisWeekLoad() {
            return thisWeekLoad;
        }
    }

    protected static class LoadData {
        public Integer load;
        public Integer acuteLoad;
        public Integer chronicLoad;
        public Calendar day;
        public int i;

        public LoadData(Calendar day,
                        Integer load,
                        Integer acuteLoad,
                        Integer chronicLoad,
                        int i
        ) {
            this.load = load;
            this.chronicLoad = chronicLoad;
            this.acuteLoad = acuteLoad;
            this.day = day;
            this.i = i;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.union(
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getWorkoutLoadSampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getTrainingAcuteLoadSampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getTrainingChronicLoadSampleProvider(device, db.getDaoSession()))
        );
    }
}
