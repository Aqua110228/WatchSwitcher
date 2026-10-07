/*  Copyright (C) 2024-2026 Severin von Wnuck-Lipinski, oddballza

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample;
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit;
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator;
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionEstimates;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

public class WeightChartFragment extends AbstractChartFragment<WeightChartFragment.WeightChartsData> {
    private int colorBackground;
    private int colorSecondaryText;

    private int totalDays;
    private WeightUnit weightUnit = WeightUnit.KILOGRAM;
    private int weightTargetKg;

    private LineChart chart;
    private TextView textTimeSpan;
    private TextView textWeightLatest;
    private static final String PREF_BODY_COMPOSITION_VALUES = "chart_weight_body_composition";

    private TextView textWeightTarget;
    private TextView textBmi;
    private TextView textBodyFat;
    private TextView textBodyWater;
    private TextView textMuscleMass;
    private TextView textBoneMass;
    private TextView textBmr;
    private TextView textImpedance;

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_weight);
    }

    @Override
    protected void init() {
        GBPrefs prefs = GBApplication.getPrefs();

        colorBackground = GBApplication.getBackgroundColor(requireContext());
        colorSecondaryText = GBApplication.getSecondaryTextColor(requireContext());

        if (prefs.getBoolean("charts_range", true))
            totalDays = 30;
        else
            totalDays = 7;

        weightUnit = prefs.getWeightUnit();

        weightTargetKg = prefs.getInt(ActivityUser.PREF_USER_GOAL_WEIGHT_KG, ActivityUser.defaultUserGoalWeightKg);
    }

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    @Override
    protected WeightChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        long tsStart = getTSStart() * 1000L;
        long tsEnd = getTSEnd() * 1000L;

        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        TimeSampleProvider<? extends WeightSample> provider = coordinator.getWeightSampleProvider(device, db.getDaoSession());
        List<? extends WeightSample> samples = provider.getAllSamples(tsStart, tsEnd);
        WeightSample latestSample = provider.getLatestSample();
        BodyCompositionCalculator.BodyComposition composition = BodyCompositionEstimates.composition(db.getDaoSession(), latestSample);
        Float bmi = BodyCompositionEstimates.bmi(db.getDaoSession(), latestSample);
        return createChartsData(samples, latestSample, composition, bmi);
    }

    @Override
    protected void renderCharts() {
        chart.animateX(ANIM_TIME, Easing.EaseInOutQuart);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    @Override
    protected void updateChartsnUIThread(WeightChartsData chartsData) {
        chart.setData(null); // workaround for https://github.com/PhilJay/MPAndroidChart/issues/2317
        chart.getXAxis().setValueFormatter(chartsData.getXValueFormatter());
        chart.getXAxis().setAvoidFirstLastClipping(true);
        chart.setData(chartsData.getData());
        textTimeSpan.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        WeightSample latestSample = chartsData.getLatestSample();
        if (latestSample != null)
            textWeightLatest.setText(formatWeight(weightFromKg(latestSample.getWeightKg())));

        textWeightTarget.setText(formatWeight(weightFromKg(weightTargetKg)));
        updateBodyComposition(latestSample, chartsData.getComposition(), chartsData.getBmi());
    }

    private void updateBodyComposition(@Nullable final WeightSample sample,
                                       @Nullable final BodyCompositionCalculator.BodyComposition composition,
                                       @Nullable final Float bmi) {
        final Set<String> enabled = GBApplication.getPrefs().getStringSet(
                PREF_BODY_COMPOSITION_VALUES,
                new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.pref_chart_weight_body_composition_default)))
        );
        // BMI only needs the weight and the height, so it does not depend on the impedance below.
        final boolean showBmi = bmi != null && enabled.contains("bmi");
        showTile(textBmi, showBmi, showBmi ? getString(R.string.body_composition_bmi_value, bmi) : getString(R.string.stats_empty_value));
        // Without an impedance there is nothing to derive; the values are hidden individually so
        // the remaining ones close ranks in the grid.
        final boolean hasImpedance = sample != null && sample.getImpedanceOhm() != null;
        if (!hasImpedance) {
            for (final TextView valueView : new TextView[]{textBodyFat, textBodyWater, textMuscleMass, textBoneMass, textBmr, textImpedance}) {
                showTile(valueView, false, getString(R.string.stats_empty_value));
            }
            return;
        }
        showTile(textBodyFat, enabled.contains("body_fat"), formatPercent(composition != null ? composition.bodyFatPercent : null));
        showTile(textBodyWater, enabled.contains("body_water"), formatPercent(composition != null ? composition.bodyWaterPercent : null));
        showTile(textMuscleMass, enabled.contains("muscle_mass"), formatOptionalWeight(composition != null ? composition.muscleMassKg : null));
        showTile(textBoneMass, enabled.contains("bone_mass"), formatOptionalWeight(composition != null ? composition.boneMassKg : null));
        showTile(textBmr, enabled.contains("bmr"), composition != null
                ? getString(R.string.body_composition_kcal, composition.basalMetabolicRate)
                : getString(R.string.stats_empty_value));
        showTile(textImpedance, enabled.contains("impedance"), getString(R.string.body_composition_ohm, sample.getImpedanceOhm()));
    }

    /**
     * Shows or hides a whole tile (the parent layout holding the line, value and label).
     */
    private void showTile(final TextView valueView, final boolean enabled, final String value) {
        valueView.setText(value);
        ((View) valueView.getParent()).setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private String formatPercent(final Float value) {
        return value != null ? getString(R.string.body_composition_percent, value) : getString(R.string.stats_empty_value);
    }

    private String formatOptionalWeight(final Float kg) {
        return kg != null ? formatWeight(weightFromKg(kg)) : getString(R.string.stats_empty_value);
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_weightchart, container, false);

        chart = rootView.findViewById(R.id.weight_chart);
        textTimeSpan = rootView.findViewById(R.id.weight_time_span_text);
        textWeightLatest = rootView.findViewById(R.id.weight_latest_text);
        textWeightTarget = rootView.findViewById(R.id.weight_target_text);
        textBmi = rootView.findViewById(R.id.weight_bmi_text);
        textBodyFat = rootView.findViewById(R.id.weight_body_fat_text);
        textBodyWater = rootView.findViewById(R.id.weight_body_water_text);
        textMuscleMass = rootView.findViewById(R.id.weight_muscle_mass_text);
        textBoneMass = rootView.findViewById(R.id.weight_bone_mass_text);
        textBmr = rootView.findViewById(R.id.weight_bmr_text);
        textImpedance = rootView.findViewById(R.id.weight_impedance_text);

        configureBarLineChartDefaults(chart);
        chart.setBackgroundColor(colorBackground);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);

        LimitLine targetLine = new LimitLine(weightFromKg(weightTargetKg));
        targetLine.setTextColor(colorSecondaryText);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(colorSecondaryText);
        xAxis.setDrawLabels(true);
        xAxis.setDrawLimitLinesBehindData(true);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextColor(colorSecondaryText);
        yAxis.addLimitLine(targetLine);
        yAxis.setDrawGridLines(true);

        refresh();

        return rootView;
    }

    private WeightChartsData createChartsData(List<? extends WeightSample> samples, WeightSample latestSample,
                                              @Nullable BodyCompositionCalculator.BodyComposition composition,
                                              @Nullable Float bmi) {
        List<Entry> entries = new ArrayList<>();
        TimestampTranslation tsTranslation = new TimestampTranslation();

        for (WeightSample sample : samples) {
            int tsSeconds = (int)(sample.getTimestamp() / 1000L);
            float weight = weightFromKg(sample.getWeightKg());

            entries.add(new Entry(tsTranslation.shorten(tsSeconds), weight));
        }

        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.menuitem_weight));
        dataSet.setLineWidth(2.2f);
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        dataSet.setCubicIntensity(0.1f);
        dataSet.setCircleRadius(5);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10);
        dataSet.setValueTextColor(colorSecondaryText);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPointLabel(Entry entry) {
                return formatWeight(entry.getY());
            }
        });

        return new WeightChartsData(new LineData(dataSet), tsTranslation, latestSample, composition, bmi);
    }

    private float weightFromKg(float weight) {
        return (float) WeightUnit.Companion.convertWeight(weight, weightUnit);
    }

    private String formatWeight(float convertedWeight) {
        return WeightUnit.Companion.formatConvertedWeight(requireContext(), convertedWeight, weightUnit);
    }

    protected static class WeightChartsData extends DefaultChartsData<LineData> {
        private final WeightSample latestSample;
        private final BodyCompositionCalculator.BodyComposition composition;
        private final Float bmi;

        public WeightChartsData(LineData lineData, TimestampTranslation tsTranslation, WeightSample latestSample,
                                @Nullable BodyCompositionCalculator.BodyComposition composition, @Nullable Float bmi) {
            super(lineData, new DateFormatter(tsTranslation));
            this.latestSample = latestSample;
            this.composition = composition;
            this.bmi = bmi;
        }

        @Nullable
        private Float getBmi() {
            return bmi;
        }

        private WeightSample getLatestSample() {
            return latestSample;
        }

        @Nullable
        private BodyCompositionCalculator.BodyComposition getComposition() {
            return composition;
        }
    }

    private static class DateFormatter extends ValueFormatter {
        private TimestampTranslation translation;
        private SimpleDateFormat format = new SimpleDateFormat("dd.MM.");
        private Calendar calendar = GregorianCalendar.getInstance();

        public DateFormatter(TimestampTranslation translation) {
            this.translation = translation;
        }

        @Override
        public String getFormattedValue(float value) {
            calendar.clear();
            calendar.setTimeInMillis(translation.toOriginalValue((int)value) * 1000L);

            return format.format(calendar.getTime());
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getWeightSampleProvider(device, db.getDaoSession()));
    }
}
