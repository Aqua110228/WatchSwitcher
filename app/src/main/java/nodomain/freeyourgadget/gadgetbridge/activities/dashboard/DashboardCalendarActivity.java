/*  Copyright (C) 2023-2024 Arjan Schrijver

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.gridlayout.widget.GridLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class DashboardCalendarActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardCalendarActivity.class);
    public static String EXTRA_TIMESTAMP = "dashboard_calendar_chosen_day";
    private final ConcurrentHashMap<Calendar, TextView> dayCells = new ConcurrentHashMap<>();
    private final ExecutorService calendarExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * Bumped on every draw() so a background fill from a month the user has since navigated
     * away from can detect it and stop applying updates.
     */
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private ValueAnimator goalsLineAnimator;
    private float goalsLineDisplayedWidth = 0f;

    @ColorInt private final int color_unknown = Color.argb(50, 128, 128, 128);
    @ColorInt private final int color_0_25 = Color.argb(128, 255, 0, 0); // Red
    @ColorInt private final int color_25_50 = Color.argb(128, 255, 128, 0); // Orange
    @ColorInt private final int color_50_75 = Color.argb(128, 255, 255, 0); // Yellow
    @ColorInt private final int color_75_100 = Color.argb(128, 0, 128, 0); // Dark green
    @ColorInt private final int color_100 = Color.argb(128, 0, 255, 0); // Green

    private boolean showAllDevices;
    private Set<String> showDeviceList;

    TextView monthTextView;
    TextView arrowLeft;
    TextView arrowRight;
    GridLayout calendarGrid;
    Calendar currentDay;
    Calendar cal;
    ImageView monthGoalsChart;
    TextView monthGoalsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_calendar);
        monthTextView = findViewById(R.id.calendar_month);
        calendarGrid = findViewById(R.id.dashboard_calendar_grid);
        monthGoalsChart = findViewById(R.id.dashboard_calendar_month_goals_chart);
        monthGoalsText = findViewById(R.id.dashboard_calendar_month_goals_text);
        currentDay = Calendar.getInstance();
        cal = Calendar.getInstance();
        long receivedTimestamp = getIntent().getLongExtra(EXTRA_TIMESTAMP, 0);
        if (receivedTimestamp != 0) {
            currentDay.setTimeInMillis(receivedTimestamp);
            cal.setTimeInMillis(receivedTimestamp);
        }

        Prefs prefs = GBApplication.getPrefs();
        showAllDevices = prefs.getBoolean("dashboard_devices_all", true);
        showDeviceList = prefs.getStringSet("dashboard_devices_multiselect", new HashSet<>());

        arrowLeft = findViewById(R.id.arrow_left);
        arrowLeft.setOnClickListener(v -> {
            cal.add(Calendar.MONTH, -1);
            draw();
        });
        arrowRight = findViewById(R.id.arrow_right);
        arrowRight.setOnClickListener(v -> {
            Calendar today = GregorianCalendar.getInstance();
            if (!DateTimeUtils.isSameMonth(today, cal)) {
                cal.add(Calendar.MONTH, 1);
                draw();
            }
        });

        draw();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Invalidate any in-flight background work and drop queued UI updates
        loadGeneration.incrementAndGet();
        calendarExecutor.shutdownNow();
        if (goalsLineAnimator != null) {
            goalsLineAnimator.cancel();
        }
    }

    private void displayColorsAsync() {
        final int generation = loadGeneration.incrementAndGet();
        final List<Map.Entry<Calendar, TextView>> entries = new ArrayList<>(dayCells.entrySet());
        // Sort by day so cells are populated in order
        entries.sort(Map.Entry.comparingByKey());
        LOG.debug("Calendar: queueing fill of {} days (gen={})", entries.size(), generation);
        calendarExecutor.execute(new CalendarFillTask(generation, entries));
    }

    private void draw() {
        // Remove previous calendar days
        dayCells.clear();
        calendarGrid.removeAllViews();
        // Update month display
        SimpleDateFormat monthFormat = new SimpleDateFormat("LLLL yyyy", Locale.getDefault());
        monthTextView.setText(monthFormat.format(cal.getTime()));
        Calendar today = GregorianCalendar.getInstance();
        today.set(Calendar.HOUR, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        if (DateTimeUtils.isSameMonth(today, cal)) {
            arrowRight.setAlpha(0.5f);
        } else {
            arrowRight.setAlpha(1);
        }
        // Calculate grid cell size for dates
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int cellSize = screenWidth / 7;
        // Determine first day that should be displayed
        Calendar drawCal = (Calendar) cal.clone();
        drawCal.set(Calendar.DAY_OF_MONTH, 1);
        int displayMonth = drawCal.get(Calendar.MONTH);
        int firstDayOfWeek = cal.getFirstDayOfWeek();
        int daysToFirstDay = (drawCal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7;
        drawCal.add(Calendar.DAY_OF_MONTH, -daysToFirstDay);
        // Determine last day that should be displayed
        Calendar lastDay = (Calendar) cal.clone();
        lastDay.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        int daysAfterMonth = (firstDayOfWeek + 7 - lastDay.get(Calendar.DAY_OF_WEEK)) % 7;
        if (daysAfterMonth == 0 && lastDay.get(Calendar.DAY_OF_WEEK) == firstDayOfWeek) {
            daysAfterMonth = 7;
        }
        lastDay.add(Calendar.DAY_OF_MONTH, daysAfterMonth);
        // Add day names header
        SimpleDateFormat dayFormat = new SimpleDateFormat("E", Locale.getDefault());
        Calendar weekdays = Calendar.getInstance();
        for (int i=0; i<7; i++) {
            int currentDayOfWeek = (firstDayOfWeek + i - 1) % 7 + 1;
            weekdays.set(Calendar.DAY_OF_WEEK, currentDayOfWeek);
            createWeekdayCell(dayFormat.format(weekdays.getTime()), cellSize);
        }
        // Loop through month days and create grid cells for them
        while (!DateTimeUtils.isSameDay(drawCal, lastDay)) {
            boolean clickable = drawCal.get(Calendar.MONTH) == displayMonth;
            if (drawCal.after(today)) clickable = false;
            createDateCell(drawCal, cellSize, clickable);
            drawCal.add(Calendar.DAY_OF_MONTH, 1);
        }
        // Asynchronously determine and display goal colors
        displayColorsAsync();
    }

    private TextView prepareGridElement(int cellSize) {
        GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL,1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1, GridLayout.FILL,1f)
        );
        int margin = cellSize / 10;
        layoutParams.width = 0;
        layoutParams.height = cellSize - 2 * margin;
        layoutParams.setMargins(margin, margin, margin, margin);
        TextView text = new TextView(this);
        text.setLayoutParams(layoutParams);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void createWeekdayCell(String day, int cellSize) {
        TextView text = prepareGridElement(cellSize);
        text.setText(day);
        calendarGrid.addView(text);
    }

    private void createDateCell(Calendar day, int cellSize, boolean clickable) {
        TextView text = prepareGridElement(cellSize);
        text.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
        if (clickable) {
            // Save textview for later coloring
            dayCells.put((Calendar) day.clone(), text);
        }
        calendarGrid.addView(text);
    }

    /**
     * The step-goal completion factor (clamped to [0, 1]) across the given devices, for one day.
     */
    private static float getStepsGoalFactorForDay(final boolean showAllDevices, final Set<String> showDeviceList, final Calendar day) {
        final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        int totalSteps = 0;
        try (DBHandler dbHandler = GBApplication.acquireDbReadOnly()) {
            for (final GBDevice dev : devices) {
                if ((showAllDevices || showDeviceList.contains(dev.getAddress())) && dev.getDeviceCoordinator().supportsStepCounter(dev)) {
                    totalSteps += (int) DailyTotals.getDailyStepsForDevice(dev, day, dbHandler);
                }
            }
        } catch (final Exception e) {
            LOG.warn("Could not calculate total amount of steps: ", e);
        }
        final float stepsGoal = new ActivityUser().getStepsGoal();
        float goalFactor = totalSteps / stepsGoal;
        if (goalFactor > 1) goalFactor = 1;
        return goalFactor;
    }

    /**
     * Computes each visible day's goal color on a background thread and applies it to its
     * cell as soon as it is known, so the month fills in progressively instead of popping in
     * all at once. Aborts early (and discards any UI updates already posted) if a newer
     * run has started in the meantime, e.g. because the user navigated to a different month.
     */
    private class CalendarFillTask implements Runnable {
        private final int generation;
        private final List<Map.Entry<Calendar, TextView>> entries;

        CalendarFillTask(final int generation, final List<Map.Entry<Calendar, TextView>> entries) {
            this.generation = generation;
            this.entries = entries;
        }

        @Override
        public void run() {
            final long startTime = System.currentTimeMillis();
            int amount_0_25 = 0;
            int amount_25_50 = 0;
            int amount_50_75 = 0;
            int amount_75_100 = 0;
            int amount_100 = 0;

            // Clear the previous month's legend and progress bar right away, instead of
            // leaving them stale while the new month loads.
            mainHandler.post(() -> {
                if (loadGeneration.get() == generation) {
                    fillLegend(0, 0, 0, 0, 0);
                    resetGoalsLine();
                }
            });

            int processed = 0;
            for (final Map.Entry<Calendar, TextView> entry : entries) {
                if (loadGeneration.get() != generation) {
                    LOG.debug("Calendar fill aborted after {}/{} days, {}ms (gen={} superseded)",
                            processed, entries.size(), System.currentTimeMillis() - startTime, generation);
                    return;
                }
                final Calendar day = entry.getKey();
                final TextView text = entry.getValue();
                // Determine day color by the amount of the steps goal reached
                final float goalFactor = getStepsGoalFactorForDay(showAllDevices, showDeviceList, day);
                @ColorInt final int dayColor;
                if (goalFactor >= 1) {
                    dayColor = color_100;
                    amount_100++;
                } else if (goalFactor >= 0.75) {
                    dayColor = color_75_100;
                    amount_75_100++;
                } else if (goalFactor >= 0.5) {
                    dayColor = color_50_75;
                    amount_50_75++;
                } else if (goalFactor >= 0.25) {
                    dayColor = color_25_50;
                    amount_25_50++;
                } else if (goalFactor > 0) {
                    dayColor = color_0_25;
                    amount_0_25++;
                } else {
                    dayColor = color_unknown;
                }
                processed++;
                // Capture the running totals so the legend grows one day at a time, in step
                // with the cells being colored in.
                // The progress bar itself is only animated once at the end (see below)
                final int runningAmount_0_25 = amount_0_25;
                final int runningAmount_25_50 = amount_25_50;
                final int runningAmount_50_75 = amount_50_75;
                final int runningAmount_75_100 = amount_75_100;
                final int runningAmount_100 = amount_100;
                mainHandler.post(() -> {
                    if (loadGeneration.get() != generation) {
                        return;
                    }
                    applyDayColor(day, text, dayColor);
                    fillLegend(runningAmount_0_25, runningAmount_25_50, runningAmount_50_75, runningAmount_75_100, runningAmount_100);
                });
            }

            final int finalAmount_0_25 = amount_0_25;
            final int finalAmount_25_50 = amount_25_50;
            final int finalAmount_50_75 = amount_50_75;
            final int finalAmount_75_100 = amount_75_100;
            final int finalAmount_100 = amount_100;
            mainHandler.post(() -> {
                if (loadGeneration.get() == generation) {
                    drawMonthGoalsLine(finalAmount_0_25, finalAmount_25_50, finalAmount_50_75, finalAmount_75_100, finalAmount_100);
                }
            });
            LOG.debug("Calendar filled {} days in {}ms (gen={})", entries.size(), System.currentTimeMillis() - startTime, generation);
        }
    }

    private void applyDayColor(final Calendar day, final TextView text, @ColorInt final int dayColor) {
        final long timestamp = day.getTimeInMillis();
        // Draw colored circle
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setShape(GradientDrawable.OVAL);
        backgroundDrawable.setColor(dayColor);
        if (DateTimeUtils.isSameDay(day, currentDay)) {
            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setShape(GradientDrawable.OVAL);
            borderDrawable.setColor(Color.TRANSPARENT);
            borderDrawable.setStroke(5, GBApplication.getTextColor(getApplicationContext()));
            LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{backgroundDrawable, borderDrawable});
            text.setBackground(layerDrawable);
        } else {
            text.setBackground(backgroundDrawable);
        }
        text.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_TIMESTAMP, timestamp);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Pop the cell in now that its data has loaded
        text.setScaleX(0.4f);
        text.setScaleY(0.4f);
        text.setAlpha(0f);
        text.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    private void fillLegend(final int amount_0_25, final int amount_25_50, final int amount_50_75, final int amount_75_100, final int amount_100) {
        Resources res = getResources();
        SpannableString line_100 = new SpannableString("■ 100%: " + res.getQuantityString(R.plurals.amount_of_days, amount_100, amount_100));
        line_100.setSpan(new ForegroundColorSpan(color_100), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString line_75_100 = new SpannableString("■ 75-100%: " + res.getQuantityString(R.plurals.amount_of_days, amount_75_100, amount_75_100));
        line_75_100.setSpan(new ForegroundColorSpan(color_75_100), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString line_50_75 = new SpannableString("■ 50-75%: " + res.getQuantityString(R.plurals.amount_of_days, amount_50_75, amount_50_75));
        line_50_75.setSpan(new ForegroundColorSpan(color_50_75), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString line_25_50 = new SpannableString("■ 25-50%: " + res.getQuantityString(R.plurals.amount_of_days, amount_25_50, amount_25_50));
        line_25_50.setSpan(new ForegroundColorSpan(color_25_50), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString line_0_25 = new SpannableString("■ 0-25%: " + res.getQuantityString(R.plurals.amount_of_days, amount_0_25, amount_0_25));
        line_0_25.setSpan(new ForegroundColorSpan(color_0_25), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        monthGoalsText.setText(builder.append(line_100).append("\n").append(line_75_100).append("\n").append(line_50_75).append("\n").append(line_25_50).append("\n").append(line_0_25));
    }

    /**
     * Clear the progress bar instantly«to its empty state, with no animation.
     */
    private void resetGoalsLine() {
        if (goalsLineAnimator != null) {
            goalsLineAnimator.cancel();
        }
        goalsLineDisplayedWidth = 0f;
        renderGoalsLine(0f, 0, 0, 0, 0, 0, 0);
    }

    private void drawMonthGoalsLine(final int amount_0_25, final int amount_25_50, final int amount_50_75, final int amount_75_100, final int amount_100) {
        int monthMaxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        final int amountOfDays = amount_0_25 + amount_25_50 + amount_50_75 + amount_75_100 + amount_100;
        int height = 40;
        int totalDrawWidth = 700 - height / 2;
        final float targetDrawWidth = totalDrawWidth * ((float) amountOfDays / monthMaxDays);

        // Animate from whatever is currently on screen to the final width, once the whole month has been computed
        if (goalsLineAnimator != null) {
            goalsLineAnimator.cancel();
        }
        goalsLineAnimator = ValueAnimator.ofFloat(goalsLineDisplayedWidth, targetDrawWidth);
        goalsLineAnimator.setDuration(200);
        goalsLineAnimator.addUpdateListener(animation -> {
            goalsLineDisplayedWidth = (float) animation.getAnimatedValue();
            renderGoalsLine(goalsLineDisplayedWidth, amountOfDays, amount_0_25, amount_25_50, amount_50_75, amount_75_100, amount_100);
        });
        goalsLineAnimator.start();
    }

    private void renderGoalsLine(final float drawWidth, final int amountOfDays, final int amount_0_25, final int amount_25_50, final int amount_50_75, final int amount_75_100, final int amount_100) {
        int width = 700;
        int height = 40;
        int totalDrawWidth = width - height / 2;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(height);
        paint.setColor(color_unknown);
        canvas.drawLine(height / 2, height / 2, totalDrawWidth, height / 2, paint);

        // 0-25%
        if (amount_0_25 > 0) {
            paint.setColor(color_0_25);
            canvas.drawLine(height / 2, height / 2, drawWidth, height / 2, paint);
        }

        // 25-50%
        if (amount_25_50 > 0) {
            paint.setColor(color_25_50);
            float barDays = amount_25_50 + amount_50_75 + amount_75_100 + amount_100;
            float barFraction = barDays / amountOfDays;
            canvas.drawLine(height / 2, height / 2, drawWidth * barFraction, height / 2, paint);
        }

        // 50-75%
        if (amount_50_75 > 0) {
            paint.setColor(color_50_75);
            float barDays = amount_50_75 + amount_75_100 + amount_100;
            float barFraction = barDays / amountOfDays;
            canvas.drawLine(height / 2, height / 2, drawWidth * barFraction, height / 2, paint);
        }

        // 75-100%
        if (amount_75_100 > 0) {
            paint.setColor(color_75_100);
            float barDays = amount_75_100 + amount_100;
            float barFraction = barDays / amountOfDays;
            canvas.drawLine(height / 2, height / 2, drawWidth * barFraction, height / 2, paint);
        }

        // 100%
        if (amount_100 > 0) {
            paint.setColor(color_100);
            float barDays = amount_100;
            float barFraction = barDays / amountOfDays;
            canvas.drawLine(height / 2, height / 2, drawWidth * barFraction, height / 2, paint);
        }

        monthGoalsChart.setImageBitmap(bitmap);
    }
}
