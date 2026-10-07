/*  Copyright (C) 2026 Dany Mestas

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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.DefaultHeartRateZones;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZones;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public final class HeartRateZoneChartUtils {

    private HeartRateZoneChartUtils() {
    }

    /** One HR-zone band. Drawn behind the HR line, never part of the legend or the value marker. */
    public static final class ZoneAreaDataSet extends LineDataSet {
        public ZoneAreaDataSet(final List<Entry> entries) {
            super(entries, null);
        }
    }

    public static final class ZoneAnalysis {
        public final int[] secondsInZone;

        public ZoneAnalysis(int[] secondsInZone) {
            this.secondsInZone = secondsInZone;
        }
    }

    /**
     * Walks chronological HR entries and accumulates time-in-zone, gap-aware.
     *
     * @param entries          Sorted ascending by x. y = HR (bpm).
     * @param zones            Resolved zone thresholds.
     * @param gapInXUnits      Gap threshold in same units as Entry.x; 0 or less counts no stretch
     *                         as a gap, so the whole series accumulates time.
     * @param xUnitsPerSecond  Conversion: 1 for seconds, 1000 for milliseconds.
     */
    public static ZoneAnalysis analyze(List<Entry> entries,
                                       HeartRateZones zones,
                                       float gapInXUnits,
                                       float xUnitsPerSecond) {
        final int[] secondsInZone = new int[6];
        if (zones == null || entries.isEmpty()) {
            return new ZoneAnalysis(secondsInZone);
        }

        float prevX = 0f;
        int prevZone = -1;
        boolean hasPrev = false;

        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            final float x = e.getX();
            final int hr = Math.round(e.getY());
            final int zone = HeartRateZonesResolver.zoneOf(hr, zones);
            final boolean gap = hasPrev && gapInXUnits > 0 && (x - prevX) > gapInXUnits;

            if (hasPrev && !gap) {
                final float dtSec = (x - prevX) / xUnitsPerSecond;
                if (dtSec > 0 && prevZone >= 0 && prevZone <= 5) {
                    secondsInZone[prevZone] += Math.round(dtSec);
                }
            }

            prevX = x;
            prevZone = zone;
            hasPrev = true;
        }

        return new ZoneAnalysis(secondsInZone);
    }

    /**
     * Builds 5 stacked-area datasets that fill below the HR signal up to each zone's bounds.
     * For zone z at any x: line y = clamp(hr, zoneBottom, zoneTop), fill terminates at zoneBottom.
     * When hr is below the zone, line sits AT zoneBottom and no area is rendered.
     * The bands are {@link ZoneAreaDataSet}s with {@link Legend.LegendForm#NONE}; add them to the chart
     * before the HR line so they are drawn behind it.
     */
    public static List<ILineDataSet> buildZoneAreas(Context ctx,
                                                    HeartRateZones zones,
                                                    List<Entry> hrEntries,
                                                    int chartMax,
                                                    YAxis.AxisDependency axisDependency) {
        final List<ILineDataSet> areas = new ArrayList<>(5);
        if (zones == null || hrEntries.isEmpty()) {
            return areas;
        }
        float peak = Float.NEGATIVE_INFINITY;
        for (final Entry hrEntry : hrEntries) {
            peak = Math.max(peak, hrEntry.getY());
        }
        for (int z = 1; z <= 5; z++) {
            final int top = HeartRateZonesResolver.upperBoundOf(z, zones, chartMax);
            final int bottom = HeartRateZonesResolver.lowerBoundOf(z, zones);
            if (top <= bottom) {
                continue;
            }
            // A zone the heart rate never enters would contribute a flat line at its lower bound,
            // which is invisible but still stretches the axis up to that bound.
            if (peak <= bottom) {
                continue;
            }
            final List<Entry> entries = new ArrayList<>(hrEntries.size());
            for (int i = 0; i < hrEntries.size(); i++) {
                final Entry hrEntry = hrEntries.get(i);
                final float clamped = Math.max(bottom, Math.min(hrEntry.getY(), top));
                entries.add(new Entry(hrEntry.getX(), clamped));
            }
            final ZoneAreaDataSet area = new ZoneAreaDataSet(entries);
            area.setForm(Legend.LegendForm.NONE);
            area.setAxisDependency(axisDependency);
            area.setDrawCircles(false);
            area.setDrawValues(false);
            area.setLineWidth(0f);
            area.setColor(Color.TRANSPARENT);
            area.setMode(LineDataSet.Mode.LINEAR);
            area.setDrawFilled(true);
            area.setFillColor(HeartRateZonesResolver.colorForZone(ctx, z));
            area.setFillAlpha(0x40);
            final float fillBottom = bottom;
            area.setFillFormatter((IFillFormatter) (dataSet, dataProvider) -> fillBottom);
            area.setHighlightEnabled(false);
            areas.add(area);
        }
        return areas;
    }

    /**
     * Appends a "Heart Rate Zones" title, a stacked horizontal bar, and one row per zone
     * (color swatch + name with bpm range + duration with percent) to the given vertical container.
     * No-op when no zone has any time accumulated.
     */
    public static void populateZoneSummary(Context ctx, LinearLayout container,
                                           int[] secondsInZone, HeartRateZones zones) {
        if (secondsInZone == null || zones == null) {
            return;
        }
        long total = 0;
        for (int z = 1; z <= 5; z++) {
            total += secondsInZone[z];
        }
        if (total <= 0) {
            return;
        }

        final int textColor = GBApplication.getTextColor(ctx);
        final int marginPx = dpToPx(ctx, 8);

        final TextView title = new TextView(ctx);
        // Naming a formula is only honest for zones this app computed; device-reported thresholds
        // arrived ready-made and their derivation is unknown here.
        final boolean reportedByDevice = zones instanceof DefaultHeartRateZones
                && ((DefaultHeartRateZones) zones).isReportedByDevice();
        title.setText(reportedByDevice
                ? ctx.getString(R.string.HeartRateZones)
                : String.format(Locale.getDefault(), "%s (%s)",
                        ctx.getString(R.string.HeartRateZones),
                        HeartRateZones.methodToString(ctx, zones.getMethod())));
        title.setTextColor(textColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        final LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(marginPx, marginPx, marginPx, marginPx / 2);
        title.setLayoutParams(titleLp);
        container.addView(title);

        final LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(ctx, 14));
        barLp.setMargins(marginPx, 0, marginPx, marginPx / 2);
        bar.setLayoutParams(barLp);
        for (int z = 1; z <= 5; z++) {
            if (secondsInZone[z] <= 0) {
                continue;
            }
            final View seg = new View(ctx);
            seg.setBackgroundColor(HeartRateZonesResolver.colorForZone(ctx, z));
            seg.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, secondsInZone[z]));
            bar.addView(seg);
        }
        container.addView(bar);

        for (int z = 1; z <= 5; z++) {
            container.addView(buildZoneRow(ctx, z, secondsInZone[z], total, zones, textColor, marginPx));
        }
    }

    private static View buildZoneRow(Context ctx, int zoneIdx, int seconds, long total,
                                     HeartRateZones zones, int textColor, int marginPx) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(marginPx, dpToPx(ctx, 2), marginPx, dpToPx(ctx, 2));
        row.setLayoutParams(rowLp);

        final View swatch = new View(ctx);
        final LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dpToPx(ctx, 12), dpToPx(ctx, 12));
        swatchLp.rightMargin = dpToPx(ctx, 8);
        swatch.setLayoutParams(swatchLp);
        swatch.setBackgroundColor(HeartRateZonesResolver.colorForZone(ctx, zoneIdx));
        row.addView(swatch);

        final TextView label = new TextView(ctx);
        label.setText(formatZoneLabel(ctx, zoneIdx, zones));
        label.setTextColor(textColor);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        final TextView value = new TextView(ctx);
        if (seconds > 0) {
            final int percent = total > 0 ? Math.round(100f * seconds / total) : 0;
            value.setText(String.format(Locale.getDefault(), "%s (%d%%)",
                    DateTimeUtils.formatDurationHoursMinutes(seconds, TimeUnit.SECONDS), percent));
        } else {
            value.setText("-");
        }
        value.setTextColor(textColor);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        value.setGravity(Gravity.END);
        value.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(value);

        return row;
    }

    private static String formatZoneLabel(Context ctx, int zoneIdx, HeartRateZones zones) {
        final String name = HeartRateZonesResolver.labelForZone(ctx, zoneIdx);
        final String range = switch (zoneIdx) {
            case 1 -> String.format(Locale.getDefault(), "< %d", zones.getZone2());
            case 2 -> ctx.getString(R.string.range_integer, zones.getZone2(), zones.getZone3());
            case 3 -> ctx.getString(R.string.range_integer, zones.getZone3(), zones.getZone4());
            case 4 -> ctx.getString(R.string.range_integer, zones.getZone4(), zones.getZone5());
            case 5 -> String.format(Locale.getDefault(), "> %d", zones.getZone5());
            default -> "";
        };
        return String.format(Locale.getDefault(), "%s (%s)", name, range);
    }

    private static int dpToPx(Context ctx, int dp) {
        final float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
