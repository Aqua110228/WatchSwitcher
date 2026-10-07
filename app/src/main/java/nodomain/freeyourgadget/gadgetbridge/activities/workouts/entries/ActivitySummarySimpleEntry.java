/*  Copyright (C) 2024-2026 José Rebelo, a0z, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;

public class ActivitySummarySimpleEntry extends ActivitySummaryEntry {
    public static final ActivitySummarySimpleEntry EMPTY = new ActivitySummarySimpleEntry("-", "string");

    @Nullable
    private final Object value;
    @NonNull
    private final String unit;

    public ActivitySummarySimpleEntry(@Nullable final Object value, @NonNull final String unit) {
        this(null, value, unit);
    }

    public ActivitySummarySimpleEntry(@Nullable final String group, @Nullable final Object value, @NonNull final String unit) {
        super(group);
        this.value = value;
        this.unit = unit;
    }

    @Nullable
    public Object getValue() {
        return value;
    }

    @NonNull
    public String getUnit() {
        return unit;
    }

    @Override
    public int getColumnSpan() {
        return 1;
    }

    @Override
    public void populate(final String key, final LinearLayout linearLayout, final WorkoutValueFormatter workoutValueFormatter) {
        final Context context = linearLayout.getContext();

        // Value
        final TextView valueTextView = new TextView(context);
        valueTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        valueTextView.setTextSize(20);
        valueTextView.setText(workoutValueFormatter.formatTileValue(value, unit));
        valueTextView.setTextColor(GBApplication.getTextColor(context));

        // Label
        final TextView labelTextView = new TextView(context);
        labelTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        labelTextView.setTextSize(12);
        labelTextView.setText(key);

        if (getColumnSpan() == 1) {
            linearLayout.addView(valueTextView);
            linearLayout.addView(labelTextView);
        } else if (getColumnSpan() == 2) {
            // Label
            labelTextView.setTextSize(14);
            labelTextView.setMaxLines(1);
            labelTextView.setEllipsize(TextUtils.TruncateAt.END);

            // Value
            valueTextView.setTextSize(16);
            valueTextView.setTypeface(Typeface.create(valueTextView.getTypeface(), Typeface.BOLD));
            valueTextView.setGravity(Gravity.END);

            final View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));

            // Layout for the labels, so the value is at the right
            final LinearLayout labelsLinearLayout = new LinearLayout(context);
            labelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            labelsLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            labelsLinearLayout.addView(labelTextView);
            labelsLinearLayout.addView(spacer);
            labelsLinearLayout.addView(valueTextView);

            linearLayout.addView(labelsLinearLayout);
        } else {
            throw new IllegalArgumentException("Invalid columnSpan " + getColumnSpan());
        }
    }
}
