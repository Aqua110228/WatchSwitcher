package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableRowEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.TableColumnSpec
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.TableSpec
import java.util.Locale

private const val TABLE_HORIZONTAL_PADDING_DP = 16
private const val COLUMN_GAP_DP = 10
private const val INDEX_COLUMN_WIDTH_DP = 24
private const val HEADER_TOP_PADDING_DP = 12
private const val HEADER_BOTTOM_PADDING_DP = 10
private const val ROW_VERTICAL_PADDING_DP = 13

private const val HEADER_TEXT_SIZE_SP = 11f
private const val HEADER_LETTER_SPACING_SP = 0.6f
private const val HEADER_UNIT_TEXT_SIZE_SP = 10f
private const val CELL_TEXT_SIZE_SP = 14f
private const val INDEX_TEXT_SIZE_SP = 13f
private const val UNIT_TEXT_SIZE_SP = 11

/** The heart rate column header is too wide for its column as a full word. */
private const val HEART_RATE_HEADER_KEY = "heart_rate"

/**
 * Draws one row of a workout table (laps, intervals, sets...) into [container], which must be a
 * vertical LinearLayout. Every row of a table has to be drawn with the same [spec], so the
 * columns line up. A header row gets a strong rule under it; a data row gets a light rule under
 * it, except the last one ([isLastRow]). Rules and text share the same 16dp inset.
 */
fun populateTableRow(
    entry: ActivitySummaryTableRowEntry,
    container: LinearLayout,
    formatter: WorkoutValueFormatter,
    spec: TableSpec,
    isLastRow: Boolean
) {
    val context = container.context
    val mutedColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "WorkoutTable")
    val primaryColor = MaterialColors.getColor(context, R.attr.textColorPrimary, "WorkoutTable")

    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        // Header names start on the same line, whether or not a unit sits under them.
        gravity = if (entry.isHeader) Gravity.TOP else Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val top = if (entry.isHeader) HEADER_TOP_PADDING_DP else ROW_VERTICAL_PADDING_DP
        val bottom = if (entry.isHeader) HEADER_BOTTOM_PADDING_DP else ROW_VERTICAL_PADDING_DP
        setPaddingRelative(
            dpToPx(context, TABLE_HORIZONTAL_PADDING_DP),
            dpToPx(context, top),
            dpToPx(context, TABLE_HORIZONTAL_PADDING_DP),
            dpToPx(context, bottom)
        )
    }

    entry.columns.forEachIndexed { index, cell ->
        val column = spec.column(index)
        val textView = if (entry.isHeader) {
            buildHeaderCell(context, cell, column, formatter, mutedColor)
        } else {
            buildDataCell(context, cell, column, formatter, primaryColor, mutedColor)
        }
        val params = if (column.fixedWidth) {
            LinearLayout.LayoutParams(dpToPx(context, INDEX_COLUMN_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, column.weight)
        }
        if (index > 0) {
            params.marginStart = dpToPx(context, COLUMN_GAP_DP)
        }
        row.addView(textView, params)
    }
    container.addView(row)

    if (entry.isHeader) {
        container.addView(buildRule(context, R.attr.table_header_rule))
    } else if (!isLastRow) {
        container.addView(buildRule(context, R.attr.row_separator))
    }
}

/**
 * The column header. A unit the cells leave off is shown under the name, small and muted, e.g.
 * "PACE" over "min/km"; a header without one is just the name.
 */
private fun buildHeaderCell(
    context: Context,
    cell: ActivitySummaryValue,
    column: TableColumnSpec,
    formatter: WorkoutValueFormatter,
    mutedColor: Int
): View {
    val name = if (cell.value() == HEART_RATE_HEADER_KEY) {
        context.getString(R.string.table_header_hr)
    } else {
        cell.format(formatter)
    }
    val nameView = TextView(context).apply {
        text = name.uppercase(Locale.getDefault())
        textSize = HEADER_TEXT_SIZE_SP
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        // letterSpacing is in em, the design value is in sp
        letterSpacing = HEADER_LETTER_SPACING_SP / HEADER_TEXT_SIZE_SP
        setTextColor(mutedColor)
        gravity = horizontalGravity(column)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }

    val unit = column.impliedUnit?.let { formatter.getDisplayUnitLabel(it) } ?: return nameView

    val unitView = TextView(context).apply {
        text = unit
        textSize = HEADER_UNIT_TEXT_SIZE_SP
        setTextColor(mutedColor)
        gravity = horizontalGravity(column)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(nameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(unitView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
}

private fun buildDataCell(
    context: Context,
    cell: ActivitySummaryValue,
    column: TableColumnSpec,
    formatter: WorkoutValueFormatter,
    primaryColor: Int,
    mutedColor: Int
): TextView {
    return TextView(context).apply {
        text = formatCell(cell, column, formatter, mutedColor)
        textSize = if (column.fixedWidth) INDEX_TEXT_SIZE_SP else CELL_TEXT_SIZE_SP
        setTextColor(if (column.fixedWidth) mutedColor else primaryColor)
        gravity = horizontalGravity(column)
        if (column.numeric) {
            // tabular figures, so digits (and decimal points) line up between rows
            fontFeatureSettings = "tnum"
            maxLines = 1
        } else {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }
}

private fun horizontalGravity(column: TableColumnSpec): Int {
    return if (column.numeric && !column.fixedWidth) Gravity.END else Gravity.START
}

/**
 * The cell text. A unit that stays (distance's m/km) is set small and muted after the number; a
 * unit the column doesn't need ([TableColumnSpec.dropUnit]) is left off entirely.
 */
private fun formatCell(
    cell: ActivitySummaryValue,
    column: TableColumnSpec,
    formatter: WorkoutValueFormatter,
    mutedColor: Int
): CharSequence {
    val value = cell.value()
    if (value !is Number) {
        // missing values ("-") and text
        return cell.format(formatter)
    }

    val unit = cell.unit()
    if (column.dropUnit) {
        return formatter.formatValue(value, unit, false)
    }

    val full = formatter.formatValue(value, unit)
    val number = formatter.formatValue(value, unit, false)
    // Durations and the like format to something that isn't "number + unit", leave those alone.
    if (full == number || !full.startsWith(number)) {
        return full
    }
    val unitText = full.substring(number.length).trim()
    if (unitText.isEmpty()) {
        return number
    }

    return SpannableStringBuilder(number).apply {
        append(' ')
        val unitStart = length
        append(unitText)
        setSpan(AbsoluteSizeSpan(UNIT_TEXT_SIZE_SP, true), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(mutedColor), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun buildRule(context: Context, colorAttr: Int): View {
    return View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 1)
        ).apply {
            marginStart = dpToPx(context, TABLE_HORIZONTAL_PADDING_DP)
            marginEnd = dpToPx(context, TABLE_HORIZONTAL_PADDING_DP)
        }
        setBackgroundColor(MaterialColors.getColor(context, colorAttr, "WorkoutTable"))
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
