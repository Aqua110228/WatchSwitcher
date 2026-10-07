package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class HydrationPeriodFragment extends HydrationFragment<HydrationPeriodFragment.HydrationData> {
    private int TOTAL_DAYS = 1;

    private TextView mDateView;
    private TextView dailyAverage;
    private TextView hydrationGoal;
    private BarChart hydrationChart;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static HydrationPeriodFragment newInstance(final int totalDays) {
        final HydrationPeriodFragment fragment = new HydrationPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 1;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_hydration_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.hydration_date_view);
        hydrationChart = rootView.findViewById(R.id.hydration_chart);
        dailyAverage = rootView.findViewById(R.id.hydration_daily_average);
        hydrationGoal = rootView.findViewById(R.id.hydration_goal);

        setupHydrationChart();
        refresh();

        return rootView;
    }

    private void setupHydrationChart() {
        hydrationChart.getDescription().setEnabled(false);
        if (TOTAL_DAYS <= 7) {
            hydrationChart.setTouchEnabled(false);
            hydrationChart.setPinchZoom(false);
        }
        hydrationChart.setDoubleTapToZoomEnabled(false);
        hydrationChart.getLegend().setEnabled(false);

        final XAxis xAxisBottom = hydrationChart.getXAxis();
        xAxisBottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisBottom.setDrawLabels(true);
        xAxisBottom.setDrawGridLines(false);
        xAxisBottom.setEnabled(true);
        xAxisBottom.setDrawLimitLinesBehindData(true);
        xAxisBottom.setTextColor(CHART_TEXT_COLOR);

        final YAxis yAxisLeft = hydrationChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setDrawTopYLabelEntry(true);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);
        yAxisLeft.setAxisMinimum(0f);

        final YAxis yAxisRight = hydrationChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setDrawAxisLine(true);
    }

    @Override
    protected HydrationData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final LocalDate endDate = toLocalDate(chartsHost.getEndDate());
        final LocalDate startDate = endDate.minusDays(TOTAL_DAYS - 1);

        final HydrationSampleProvider provider = device.getDeviceCoordinator().getHydrationSampleProvider(device, db.getDaoSession());
        final Map<Integer, Double> totals = provider != null
                ? provider.getDayTotals(HydrationSampleProvider.toDay(startDate), HydrationSampleProvider.toDay(endDate))
                : Collections.emptyMap();

        final List<HydrationDay> days = new ArrayList<>(TOTAL_DAYS);
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final Double total = totals.get(HydrationSampleProvider.toDay(date));
            days.add(new HydrationDay(date, total != null ? Math.max(total, 0) : 0));
        }

        return new HydrationData(days, new ActivityUser().getHydrationGoalMl(), HydrationUnit.forDevice(device));
    }

    @Override
    protected void updateChartsnUIThread(HydrationData data) {
        final Context context = requireContext();
        final HydrationUnit unit = data.unit;

        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));
        hydrationChart.setData(null);

        final List<BarEntry> entries = new ArrayList<>();
        double totalMl = 0;
        for (int i = 0; i < data.days.size(); i++) {
            final HydrationDay day = data.days.get(i);
            entries.add(new BarEntry(i, (float) unit.fromMl(day.totalMl)));
            totalMl += day.totalMl;
        }
        final int color = ContextCompat.getColor(context, R.color.hydration_color);
        final BarDataSet set = new BarDataSet(entries, getTitle());
        set.setDrawValues(true);
        set.setColors(color);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(final float value) {
                //noinspection MalformedFormatString
                return String.format(Locale.getDefault(), "%." + unit.getDecimals() + "f", value);
            }
        });

        final float goal = (float) unit.fromMl(data.goalMl);
        final YAxis yAxisLeft = hydrationChart.getAxisLeft();
        yAxisLeft.removeAllLimitLines();
        final LimitLine goalLine = new LimitLine(goal);
        goalLine.setLineColor(color);
        goalLine.setLineWidth(1.5f);
        goalLine.enableDashedLine(15f, 10f, 0f);
        yAxisLeft.addLimitLine(goalLine);
        yAxisLeft.setAxisMaximum((float) Math.max(set.getYMax() * 1.1, goal * 1.1));

        hydrationChart.getXAxis().setValueFormatter(getDayValueFormatter(data));

        final BarData barData = new BarData(set);
        barData.setValueTextColor(TEXT_COLOR);
        barData.setValueTextSize(10f);
        if (TOTAL_DAYS > 7) {
            hydrationChart.setRenderer(new AngledLabelsChartRenderer(hydrationChart, hydrationChart.getAnimator(), hydrationChart.getViewPortHandler()));
        }
        hydrationChart.setData(barData);

        dailyAverage.setText(unit.format(context, data.days.isEmpty() ? 0 : totalMl / data.days.size()));
        hydrationGoal.setText(unit.format(context, data.goalMl));
    }

    private ValueFormatter getDayValueFormatter(final HydrationData data) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TOTAL_DAYS > 7 ? "dd" : "EEE", Locale.getDefault());
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                final int index = (int) value;
                if (index < 0 || index >= data.days.size()) {
                    return "";
                }
                return data.days.get(index).date.format(formatter);
            }
        };
    }

    @Override
    protected void renderCharts() {
        hydrationChart.invalidate();
    }

    protected static class HydrationDay {
        private final LocalDate date;
        private final double totalMl;

        private HydrationDay(final LocalDate date, final double totalMl) {
            this.date = date;
            this.totalMl = totalMl;
        }
    }

    protected static class HydrationData extends ChartsData {
        private final List<HydrationDay> days;
        private final int goalMl;
        private final HydrationUnit unit;

        protected HydrationData(final List<HydrationDay> days, final int goalMl, final HydrationUnit unit) {
            this.days = days;
            this.goalMl = goalMl;
            this.unit = unit;
        }
    }
}
