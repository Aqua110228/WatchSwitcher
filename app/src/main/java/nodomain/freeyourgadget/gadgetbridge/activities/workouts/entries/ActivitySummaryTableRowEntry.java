package nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries;

import android.widget.LinearLayout;

import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutTableRendererKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;

public class ActivitySummaryTableRowEntry extends ActivitySummaryEntry {
    private final List<ActivitySummaryValue> columns;
    private final boolean isHeader;
    private final boolean boldFirstColumn;

    public ActivitySummaryTableRowEntry(final List<ActivitySummaryValue> columns) {
        this(null, columns, false, false);
    }

    public ActivitySummaryTableRowEntry(final String group,
                                        final List<ActivitySummaryValue> columns,
                                        final boolean isHeader,
                                        final boolean boldFirstColumn) {
        super(group);
        this.columns = columns;
        this.isHeader = isHeader;
        this.boldFirstColumn = boldFirstColumn;
    }

    @Override
    public int getColumnSpan() {
        return 2;
    }

    public List<ActivitySummaryValue> getColumns() {
        return columns;
    }

    public boolean isHeader() {
        return isHeader;
    }

    /**
     * Draws this row on its own, as if it were a whole table. Rows of a real table should be drawn
     * with the {@link TableSpec} of the entire table instead, so the columns line up.
     */
    @Override
    public void populate(final String key, final LinearLayout linearLayout, final WorkoutValueFormatter workoutValueFormatter) {
        populate(linearLayout, workoutValueFormatter, TableSpec.from(Collections.singletonList(this)), true);
    }

    public void populate(final LinearLayout linearLayout,
                         final WorkoutValueFormatter workoutValueFormatter,
                         final TableSpec spec,
                         final boolean isLastRow) {
        WorkoutTableRendererKt.populateTableRow(this, linearLayout, workoutValueFormatter, spec, isLastRow);
    }
}
