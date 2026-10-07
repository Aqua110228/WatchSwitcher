/*  Copyright (C) 2026 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.util

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.gridlayout.widget.GridLayout
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryProgressEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableRowEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.TableSpec

class GridTableBuilder @JvmOverloads constructor(
    private val context: Context,
    private val workoutValueFormatter: WorkoutValueFormatter = WorkoutValueFormatter()
) {
    private var cellNumber = 0
    private val columnSpans = mutableListOf<Int>()

    // Parallel to the grid's children: table rows draw their own rules, so they get no gray gaps.
    private val tableRowFlags = mutableListOf<Boolean>()

    // Rows of the table being collected. They are drawn together once it is complete, because
    // the column layout depends on all of its rows.
    private val pendingTable = mutableListOf<PendingTableRow>()

    private class PendingTableRow(val entry: ActivitySummaryTableRowEntry, val layout: LinearLayout)

    private val gridLayout = GridLayout(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.gauge_line_color))
        columnCount = 2
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun addEntry(key: String, entry: ActivitySummaryEntry?) {
        val entry = entry ?: ActivitySummarySimpleEntry.EMPTY

        // Anything but a table row ends the current table, and a header starts a new one.
        val tableRow = entry as? ActivitySummaryTableRowEntry
        if (tableRow == null || tableRow.isHeader) {
            flushTable()
        }

        val columnSpan = entry.columnSpan
        if (columnSpan == 2 && cellNumber % 2 != 0) {
            cellNumber++
        }

        val compact = entry is ActivitySummaryTableRowEntry || entry is ActivitySummaryProgressEntry
        val linearLayout = generateLinearLayout(cellNumber, columnSpan, compact, tableRow != null)
        if (tableRow != null) {
            pendingTable.add(PendingTableRow(tableRow, linearLayout))
        } else {
            entry.populate(key, linearLayout, workoutValueFormatter)
        }
        gridLayout.addView(linearLayout)
        columnSpans.add(columnSpan)
        tableRowFlags.add(tableRow != null)
        cellNumber += columnSpan
    }

    private fun flushTable() {
        if (pendingTable.isEmpty()) {
            return
        }
        val spec = TableSpec.from(pendingTable.map { it.entry })
        pendingTable.forEachIndexed { index, row ->
            row.entry.populate(row.layout, workoutValueFormatter, spec, index == pendingTable.lastIndex)
        }
        pendingTable.clear()
    }

    fun build(): GridLayout {
        flushTable()

        if (gridLayout.isNotEmpty() && cellNumber % 2 != 0) {
            // When in an odd number of cells, add an empty one to prevent a gray hole from showing up
            val emptyLayout = generateLinearLayout(cellNumber, 1)
            ActivitySummarySimpleEntry("", "string").populate("", emptyLayout, workoutValueFormatter)
            gridLayout.addView(emptyLayout)
            columnSpans.add(1)
            tableRowFlags.add(false)
        }

        // Then, adjust the bottom margin for the last row
        var adjustedColumns = 0
        if (gridLayout.isNotEmpty()) {
            for (i in gridLayout.childCount - 1 downTo 0) {
                val layoutParams = gridLayout.getChildAt(i).layoutParams
                if (layoutParams is GridLayout.LayoutParams) {
                    // A table's last row has no gray strip under it, it ends on its own rules.
                    if (!tableRowFlags[i]) {
                        layoutParams.bottomMargin = dpToPx(2)
                    }
                    adjustedColumns += columnSpans[i]

                    if (adjustedColumns >= gridLayout.columnCount) {
                        break
                    }
                }
            }
        }

        return gridLayout
    }

    private fun generateLinearLayout(i: Int, columnSize: Int, compact: Boolean = false, tableRow: Boolean = false): LinearLayout {
        return LinearLayout(context).apply {
            val layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, columnSize, GridLayout.FILL, 1f)
            )
            layoutParams.width = 0
            this.layoutParams = layoutParams
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Table rows (laps, intervals, sets) and zone progress bars pack many rows on
            // screen, so give them less room than a regular key/value summary cell.
            val verticalPadding = if (compact) 8 else 15
            // Table rows do their own padding and draw their own rules, inset from the edges.
            if (tableRow) {
                setPadding(0, 0, 0, 0)
            } else {
                setPadding(dpToPx(15), dpToPx(verticalPadding), dpToPx(15), dpToPx(verticalPadding))
            }
            setBackgroundColor(GBApplication.getWindowBackgroundColor(context))

            // A full-width (2-column-span) cell has no adjacent column, so it gets no side border.
            val marginLeft = if (columnSize == 2 || i % 2 == 0) 0 else 1
            val marginRight = if (columnSize == 2) 0 else if (i % 2 == 0) 1 else 0
            val marginTop = 2
            val marginBottom = 0 // will be changed to 2 for the last row

            if (tableRow) {
                layoutParams.setMargins(0, 0, 0, 0)
            } else {
                layoutParams.setMargins(dpToPx(marginLeft), dpToPx(marginTop), dpToPx(marginRight), dpToPx(marginBottom))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
