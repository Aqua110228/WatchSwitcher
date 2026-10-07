package nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;

public class ActivitySummaryProgressEntry extends ActivitySummarySimpleEntry {
    private final int progress;
    private int color;

    public ActivitySummaryProgressEntry(final Object value, final String unit, final int progress) {
        this(null, value, unit, progress);
    }

    public ActivitySummaryProgressEntry(final String group, final Object value, final String unit, final int progress) {
        super(group, value, unit);
        this.progress = progress;
    }

    public ActivitySummaryProgressEntry(final Object value, final String unit, final int progress, final int color) {
        this(null, value, unit, progress);
        this.color = color;
    }

    public int getProgress() {
        return progress;
    }

    @Override
    public int getColumnSpan() {
        return 2;
    }

    @Override
    public void populate(final String key, final LinearLayout linearLayout, final WorkoutValueFormatter workoutValueFormatter) {
        final Context context = linearLayout.getContext();

        // Label
        final TextView labelTextView = new TextView(context);
        labelTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        labelTextView.setTextSize(12);
        labelTextView.setText(key);

        // Value
        final TextView valueTextView = new TextView(context);
        valueTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        valueTextView.setTextSize(12);
        valueTextView.setTextColor(GBApplication.getTextColor(context));
        valueTextView.setGravity(Gravity.END);
        valueTextView.setText(workoutValueFormatter.formatValue(getValue(), getUnit()));

        // Layout for the labels, so the value is at the right
        final LinearLayout labelsLinearLayout = new LinearLayout(context);
        labelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        labelsLinearLayout.addView(labelTextView);
        labelsLinearLayout.addView(valueTextView);

        final LinearLayout progressLayout = new LinearLayout(context);
        final LinearProgressIndicator progressBar = new LinearProgressIndicator(context);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setTrackThickness((int) (10 * context.getResources().getDisplayMetrics().density));
        progressBar.setTrackCornerRadius((int) (4 * context.getResources().getDisplayMetrics().density));
        progressBar.setTrackColor(ContextCompat.getColor(context, R.color.gauge_line_color));
        progressBar.setIndicatorColor(color != 0 ? color : GBApplication.getTextColor(context));
        progressBar.setTrackStopIndicatorSize(0);
        progressBar.setIndicatorTrackGapSize(0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressLayout.addView(progressBar, params);

        linearLayout.addView(labelsLinearLayout);
        linearLayout.addView(progressLayout);
    }
}
