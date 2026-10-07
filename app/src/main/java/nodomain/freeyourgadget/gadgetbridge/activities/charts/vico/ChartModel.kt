package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

/**
 * A (x, y) data point. [x] is usually an epoch-second timestamp.
 */
data class ChartPoint(val x: Double, val y: Double)

/**
 * How one [ChartSeries] should be drawn.
 */
sealed interface SeriesStyle {
    /**
     * A line, optionally filled underneath (an area chart) or curved.
     */
    data class Line(
        val color: Int,
        val filled: Boolean = false,
        val curved: Boolean = false,
    ) : SeriesStyle

    /**
     * A column (bar). [stackKey] groups columns that stack together at the same x value.
     */
    data class Column(
        val color: Int,
        val stackKey: String? = null,
    ) : SeriesStyle
}

/**
 * One drawable series: a label, its points, and the style to draw them.
 */
data class ChartSeries(
    val key: String,
    val label: String,
    val points: List<ChartPoint>,
    val style: SeriesStyle,
)

/**
 * How to format an axis' values.
 */
enum class ChartValueFormat {
    TIME_OF_DAY,
    DATE,
    DURATION_SECONDS,
    DECIMAL,
    INTEGER,
}

data class AxisSpec(
    val format: ChartValueFormat = ChartValueFormat.DECIMAL,
    val minimum: Double? = null,
    val maximum: Double? = null,
)

/**
 * A horizontal limit line, e.g. goal / average.
 */
data class LimitLineSpec(
    val value: Double,
    val color: Int,
    val dashed: Boolean = true,
)

/**
 * One legend. Usually mirrors a [ChartSeries] one-to-one, but might not (e.g. stress charts).
 */
data class LegendItemSpec(
    val label: String,
    val color: Int,
)

/**
 * A library-independent chart specification.
 */
data class ChartSpec(
    val series: List<ChartSeries>,
    val xAxis: AxisSpec = AxisSpec(format = ChartValueFormat.TIME_OF_DAY),
    val yAxis: AxisSpec = AxisSpec(),
    val limitLines: List<LimitLineSpec> = emptyList(),
    val legend: List<LegendItemSpec> = emptyList(),
) {
    val isEmpty: Boolean get() = series.all { it.points.isEmpty() }

    companion object {
        /**
         * A [ChartSpec] with no series, rendered as the "no data" placeholder.
         */
        val EMPTY = ChartSpec(series = emptyList())
    }
}
