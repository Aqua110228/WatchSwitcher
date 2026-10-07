/*
    Copyright (C) 2026 Christian Breiteneder

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.LimitLine;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericBloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.BloodPressureExportHelper;

public class BloodPressureChartFragment extends AbstractChartFragment<BloodPressureChartFragment.BloodPressureChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(BloodPressureChartFragment.class);

    static int DATA_INVALID = -1;

    private int BACKGROUND_COLOR;
    private int CHART_TEXT_COLOR;
    private int TEXT_COLOR;
    private int LEGEND_TEXT_COLOR;

    private TimestampTranslation tsTranslation;

    private TextView mDateView;
    private LineChart mChart;
    private LinearLayout mStatsContainer;
    private LinearLayout mManualMeasurements;
    private LinearLayout mManualMeasurementsList;
    private BloodPressureChartsData currentData;

    @Override
    protected void init() {
        BACKGROUND_COLOR = GBApplication.getBackgroundColor(requireContext());
        LEGEND_TEXT_COLOR = TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_blood_pressure_chart, container, false);

        mDateView = rootView.findViewById(R.id.date_view);
        mChart = rootView.findViewById(R.id.blood_pressure_line_chart);
        mStatsContainer = rootView.findViewById(R.id.bp_stats_container);
        mManualMeasurements = rootView.findViewById(R.id.manualMeasurements);
        mManualMeasurementsList = rootView.findViewById(R.id.manualMeasurementsList);

        mManualMeasurements.setVisibility(View.GONE);
        setupLineChart();

        FloatingActionButton exportFab = rootView.findViewById(R.id.bp_export_fab);
        exportFab.setOnClickListener(v -> showExportDialog());

        refresh();
        return rootView;
    }

    @Override
    protected BloodPressureChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        int startTs = (int) (day.getTimeInMillis() / 1000);
        int endTs = startTs + 24 * 60 * 60 - 1;
        tsTranslation = new TimestampTranslation();
        tsTranslation.shorten(startTs);
        return fetchBloodPressureData(db, device, startTs, endTs);
    }

    static int getPulseRate(final BloodPressureSample sample) {
        if (sample instanceof GenericBloodPressureSample) {
            final Integer pulseRate = ((GenericBloodPressureSample) sample).getPulseRate();
            if (pulseRate != null) {
                return pulseRate;
            }
        }
        return DATA_INVALID;
    }

    protected LineDataSet createDataSet(final List<Entry> values, String label, int color) {
        final LineDataSet lineDataSet = new LineDataSet(values, label);
        lineDataSet.setColor(color);
        lineDataSet.setDrawCircles(true);
        lineDataSet.setCircleColor(color);
        lineDataSet.setCircleRadius(3f);
        lineDataSet.setDrawCircleHole(false);
        lineDataSet.setLineWidth(2.2f);
        lineDataSet.setFillAlpha(255);
        lineDataSet.setValueTextColor(TEXT_COLOR);
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.ROOT, "%d", (int) value);
            }
        });
        return lineDataSet;
    }

    @Override
    protected void updateChartsnUIThread(BloodPressureChartsData data) {
        currentData = data;
        mManualMeasurementsList.removeAllViews();
        mManualMeasurements.setVisibility(View.GONE);

        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final String average = (data.systolicAvg > 0 && data.diastolicAvg > 0)
                ? getString(R.string.blood_pressure_avg_format, data.systolicAvg, data.diastolicAvg)
                : emptyValue;

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(data.systolicLast > 0 ? String.valueOf(data.systolicLast) : emptyValue, getString(R.string.blood_pressure_systolic)));
        stats.add(new StatTileData(data.diastolicLast > 0 ? String.valueOf(data.diastolicLast) : emptyValue, getString(R.string.blood_pressure_diastolic)));
        stats.add(new StatTileData(average, getString(R.string.hr_average)));
        stats.add(new StatTileData(String.valueOf(data.measurementCount), getString(R.string.blood_pressure_measurement_count)));
        mStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(mStatsContainer, requireContext(), stats, 0);

        mChart.setData(null); // workaround for https://github.com/PhilJay/MPAndroidChart/issues/2317
        mChart.getAxisLeft().removeAllLimitLines();

        Date date = new Date((long) getTSEnd() * 1000);
        String formattedDate = new SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(date);
        mDateView.setText(formattedDate);

        final int systolicColor = ContextCompat.getColor(requireContext(), R.color.blood_pressure_systolic_color);
        final int diastolicColor = ContextCompat.getColor(requireContext(), R.color.blood_pressure_diastolic_color);
        final int heartRateColor = ContextCompat.getColor(requireContext(), R.color.chart_line_heart_rate);

        final List<ILineDataSet> lineDataSets = new ArrayList<>();
        final List<Entry> systolicEntries = new ArrayList<>();
        final List<Entry> diastolicEntries = new ArrayList<>();
        final List<Entry> heartRateEntries = new ArrayList<>();

        for (final BloodPressureSample sample : data.samples) {
            int ts = (int) (sample.getTimestamp() / 1000L);
            int tsShorten = tsTranslation.shorten(ts);

            if (sample.getBpSystolic() > 0) {
                systolicEntries.add(new Entry(tsShorten, sample.getBpSystolic()));
            }
            if (sample.getBpDiastolic() > 0) {
                diastolicEntries.add(new Entry(tsShorten, sample.getBpDiastolic()));
            }
            final int pulseRate = getPulseRate(sample);
            if (pulseRate > 0) {
                heartRateEntries.add(new Entry(tsShorten, pulseRate));
            }
        }

        final List<LegendEntry> legendEntries = new ArrayList<>(3);
        final LegendEntry systolicEntry = new LegendEntry();
        systolicEntry.label = getString(R.string.blood_pressure_systolic);
        systolicEntry.formColor = systolicColor;
        legendEntries.add(systolicEntry);
        final LegendEntry diastolicEntry = new LegendEntry();
        diastolicEntry.label = getString(R.string.blood_pressure_diastolic);
        diastolicEntry.formColor = diastolicColor;
        legendEntries.add(diastolicEntry);
        if (!heartRateEntries.isEmpty()) {
            final LegendEntry heartRateEntry = new LegendEntry();
            heartRateEntry.label = getString(R.string.heart_rate);
            heartRateEntry.formColor = heartRateColor;
            legendEntries.add(heartRateEntry);
        }
        mChart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        mChart.getLegend().setCustom(legendEntries);

        if (!systolicEntries.isEmpty()) {
            lineDataSets.add(createDataSet(systolicEntries, getString(R.string.blood_pressure_systolic), systolicColor));
        }
        if (!diastolicEntries.isEmpty()) {
            lineDataSets.add(createDataSet(diastolicEntries, getString(R.string.blood_pressure_diastolic), diastolicColor));
        }
        if (!heartRateEntries.isEmpty()) {
            final LineDataSet heartRateDataSet = createDataSet(heartRateEntries, getString(R.string.heart_rate), heartRateColor);
            heartRateDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            lineDataSets.add(heartRateDataSet);
        }

        mChart.getXAxis().setValueFormatter(new SampleXLabelFormatter(tsTranslation, "HH:mm"));

        if (!lineDataSets.isEmpty()) {
            final LineData lineData = new LineData(lineDataSets);
            mChart.setData(lineData);
        }

        if (data.systolicAvg > 0 && GBApplication.getPrefs().getBoolean("charts_show_average", true)) {
            final LimitLine avgLine = new LimitLine(data.systolicAvg);
            avgLine.setLineColor(Color.GRAY);
            avgLine.setLineWidth(1.5f);
            avgLine.enableDashedLine(15f, 10f, 0f);
            mChart.getAxisLeft().addLimitLine(avgLine);
        }
    }

    @Override
    public String getTitle() {
        return requireContext().getString(R.string.blood_pressure);
    }

    private void showExportDialog() {
        if (currentData == null || currentData.samples.isEmpty()) {
            return;
        }
        String dateLabel = mDateView.getText().toString();
        String[] options = {"PDF", "CSV"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.appmanager_app_share)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        BloodPressureExportHelper.exportPdf(requireContext(), currentData.samples, dateLabel, getWhiteChartBitmap());
                    } else {
                        BloodPressureExportHelper.exportCsv(requireContext(), currentData.samples);
                    }
                })
                .show();
    }

    private Bitmap getWhiteChartBitmap() {
        final int heartRateColor = ContextCompat.getColor(requireContext(), R.color.chart_line_heart_rate);

        // Temporarily switch to light colors for PDF export
        mChart.setBackgroundColor(0xFFFFFFFF);
        mChart.getXAxis().setTextColor(0xFF000000);
        mChart.getAxisLeft().setTextColor(0xFF000000);
        mChart.getLegend().setTextColor(0xFF000000);
        mChart.invalidate();

        Bitmap bitmap = mChart.getChartBitmap();

        // Restore original colors
        mChart.setBackgroundColor(BACKGROUND_COLOR);
        mChart.getXAxis().setTextColor(CHART_TEXT_COLOR);
        mChart.getAxisLeft().setTextColor(CHART_TEXT_COLOR);
        mChart.getAxisRight().setTextColor(heartRateColor);
        mChart.getAxisRight().setAxisLineColor(heartRateColor);
        mChart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        mChart.invalidate();

        return bitmap;
    }

    private void setupLineChart() {
        mChart.setBackgroundColor(BACKGROUND_COLOR);
        mChart.getDescription().setText("");

        final XAxis xAxisBottom = mChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);
        xAxisBottom.setAxisMinimum(0f);
        xAxisBottom.setAxisMaximum(86400f);
        xAxisBottom.setLabelCount(7, true);

        final int heartRateColor = ContextCompat.getColor(requireContext(), R.color.chart_line_heart_rate);

        final YAxis yAxisLeft = mChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMaximum(200f);
        yAxisLeft.setAxisMinimum(40f);
        yAxisLeft.setDrawTopYLabelEntry(false);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);
        yAxisLeft.setEnabled(true);
        final String unitMmHg = getString(R.string.unit_millimetre_of_mercury);
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.ROOT, "%d " + unitMmHg, (int) value);
            }
        });

        final YAxis yAxisRight = mChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(true);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
        yAxisRight.setDrawTopYLabelEntry(false);
        yAxisRight.setTextColor(heartRateColor);
        yAxisRight.setAxisLineColor(heartRateColor);
        yAxisRight.setAxisMaximum(HeartRateUtils.getInstance().getMaxHeartRate());
        yAxisRight.setAxisMinimum(HeartRateUtils.getInstance().getMinHeartRate());
        final String unitBpm = getString(R.string.bpm);
        yAxisRight.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.ROOT, "%d " + unitBpm, (int) value);
            }
        });
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        mChart.invalidate();
    }

    private List<? extends BloodPressureSample> getSamples(final DBHandler db, final GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BloodPressureSample> sampleProvider = coordinator.getBloodPressureSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    private BloodPressureChartsData fetchBloodPressureData(DBHandler db, GBDevice device, int startTs, int endTs) {
        List<? extends BloodPressureSample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator systolicAccumulator = new Accumulator();
        final Accumulator diastolicAccumulator = new Accumulator();
        int systolicLast = DATA_INVALID;
        int diastolicLast = DATA_INVALID;

        for (final BloodPressureSample sample : samples) {
            if (sample.getBpSystolic() > 0) {
                systolicAccumulator.add(sample.getBpSystolic());
                systolicLast = sample.getBpSystolic();
            }
            if (sample.getBpDiastolic() > 0) {
                diastolicAccumulator.add(sample.getBpDiastolic());
                diastolicLast = sample.getBpDiastolic();
            }
        }

        final int systolicAvg = systolicAccumulator.getCount() > 0 ? (int) Math.round(systolicAccumulator.getAverage()) : DATA_INVALID;
        final int diastolicAvg = diastolicAccumulator.getCount() > 0 ? (int) Math.round(diastolicAccumulator.getAverage()) : DATA_INVALID;
        final int measurementCount = samples.size();

        return new BloodPressureChartsData(samples, systolicAvg, diastolicAvg, systolicLast, diastolicLast, measurementCount);
    }

    protected static class BloodPressureChartsData extends ChartsData {
        public List<? extends BloodPressureSample> samples;
        public final int systolicAvg;
        public final int diastolicAvg;
        public final int systolicLast;
        public final int diastolicLast;
        public final int measurementCount;

        public BloodPressureChartsData(List<? extends BloodPressureSample> samples, int systolicAvg, int diastolicAvg,
                                       int systolicLast, int diastolicLast, int measurementCount) {
            this.samples = samples;
            this.systolicAvg = systolicAvg;
            this.diastolicAvg = diastolicAvg;
            this.systolicLast = systolicLast;
            this.diastolicLast = diastolicLast;
            this.measurementCount = measurementCount;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getBloodPressureSampleProvider(device, db.getDaoSession()));
    }
}
