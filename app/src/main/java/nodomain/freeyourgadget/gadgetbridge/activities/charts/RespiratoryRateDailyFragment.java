package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;

public class RespiratoryRateDailyFragment extends RespiratoryRateFragment<RespiratoryRateFragment.RespiratoryRateDay> {
    protected static final Logger LOG = LoggerFactory.getLogger(RespiratoryRateDailyFragment.class);

    private TextView mDateView;
    private LinearLayout statsContainer;
    private LineChart respiratoryRateChart;

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_respiratory_rate, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.rr_date_view);
        statsContainer = rootView.findViewById(R.id.respiratory_rate_daily_stats_container);
        respiratoryRateChart = rootView.findViewById(R.id.respiratory_rate_line_chart);
        setupRespiratoryRateChart();
        refresh();

        return rootView;
    }

        @Override
    public String getTitle() {
        return getString(R.string.respiratoryrate);
    }

    @Override
    protected RespiratoryRateDay refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(chartsHost.getEndDate());
        List<RespiratoryRateDay> stepsDayList = getMyRespiratoryRateDaysData(db, day, device);
        if (stepsDayList.isEmpty()) {
            LOG.error("Failed to get RespiratoryRateDay for {}", day);
            return new RespiratoryRateDay(day, new ArrayList<>(), new ArrayList<>(), true);
        } else {
            return stepsDayList.get(0);
        }
    }

    @Override
    protected void updateChartsnUIThread(RespiratoryRateFragment.RespiratoryRateDay respiratoryRateDay) {
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(respiratoryRateDay.day.getTime());
        mDateView.setText(formattedDate);
        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(
                respiratoryRateDay.awakeRateAvg > 0 ? String.valueOf(respiratoryRateDay.awakeRateAvg) : emptyValue,
                getString(R.string.sleep_colored_stats_awake_avg)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.sleepRateAvg > 0 ? String.valueOf(respiratoryRateDay.sleepRateAvg) : emptyValue,
                getString(R.string.sleep_avg)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.rateLowest > 0 ? String.valueOf(respiratoryRateDay.rateLowest) : emptyValue,
                getString(R.string.lowest)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.rateHighest > 0 ? String.valueOf(respiratoryRateDay.rateHighest) : emptyValue,
                getString(R.string.highest)
        ));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        // Chart
        final List<LegendEntry> legendEntries = new ArrayList<>(1);
        final LegendEntry respiratoryRateEntry = new LegendEntry();
        respiratoryRateEntry.label = getString(R.string.respiratoryrate);
        respiratoryRateEntry.formColor = ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color);
        legendEntries.add(respiratoryRateEntry);
        respiratoryRateChart.getLegend().setTextColor(TEXT_COLOR);
        respiratoryRateChart.getLegend().setCustom(legendEntries);

        final List<ILineDataSet> lineDataSets = new ArrayList<>();
        List<Entry> lineEntries = new ArrayList<>();
        final TimestampTranslation tsTranslation = new TimestampTranslation();
        int lastTsShorten = 0;
        for (final RespiratoryRateSample sample : respiratoryRateDay.respiratoryRateSamples) {
            int ts = (int) (sample.getTimestamp() / 1000L);
            int tsShorten = tsTranslation.shorten(ts);
            if (lastTsShorten == 0 || (tsShorten - lastTsShorten) <= 300) {
                lineEntries.add(new Entry(tsShorten, (int) sample.getRespiratoryRate()));
            } else {
                if (!lineEntries.isEmpty()) {
                    List<Entry> clone = new ArrayList<>(lineEntries.size());
                    clone.addAll(lineEntries);
                    lineDataSets.add(createDataSet(clone));
                    lineEntries.clear();
                }
            }
            lastTsShorten = tsShorten;
            lineEntries.add(new Entry(tsShorten, (int) sample.getRespiratoryRate()));
        }

        if (!lineEntries.isEmpty()) {
            lineDataSets.add(createDataSet(lineEntries));
        }

        respiratoryRateChart.getXAxis().setValueFormatter(new SampleXLabelFormatter(tsTranslation, "HH:mm"));
        if (respiratoryRateDay.rateLowest > 0 && respiratoryRateDay.rateHighest > 0) {
            final YAxis yAxisLeft = respiratoryRateChart.getAxisLeft();
            yAxisLeft.setAxisMaximum(Math.max(respiratoryRateDay.rateHighest + 3, 20));
        }

        final LineDataSet lineDataSet = new LineDataSet(lineEntries, getString(R.string.respiratoryrate));
        lineDataSet.setColor(ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color));
        lineDataSet.setDrawCircles(false);
        lineDataSet.setLineWidth(2f);
        lineDataSet.setFillAlpha(255);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color));
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawValues(false);
        lineDataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        lineDataSets.add(lineDataSet);
        final LineData lineData = new LineData(lineDataSets);
        respiratoryRateChart.setData(lineData);
    }

    protected LineDataSet createDataSet(final List<Entry> values) {
        final LineDataSet lineDataSet = new LineDataSet(values, getString(R.string.respiratoryrate));
        lineDataSet.setColor(ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color));
        lineDataSet.setDrawCircles(false);
        lineDataSet.setLineWidth(2f);
        lineDataSet.setFillAlpha(255);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color));
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawValues(false);
        lineDataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        return lineDataSet;
    }

    @Override
    protected void renderCharts() {
        respiratoryRateChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    private void setupRespiratoryRateChart() {
        respiratoryRateChart.getDescription().setEnabled(false);
        respiratoryRateChart.setDoubleTapToZoomEnabled(false);

        final XAxis xAxisBottom = respiratoryRateChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);
        xAxisBottom.setAxisMinimum(0f);
        xAxisBottom.setAxisMaximum(86400f);
        xAxisBottom.setLabelCount(7, true);

        final YAxis yAxisLeft = respiratoryRateChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMinimum(0);
        yAxisLeft.setAxisMaximum(20);
        yAxisLeft.setDrawTopYLabelEntry(true);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisRight = respiratoryRateChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }
}
