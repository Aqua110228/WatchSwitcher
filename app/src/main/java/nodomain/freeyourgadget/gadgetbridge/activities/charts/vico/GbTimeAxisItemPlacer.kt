package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import kotlin.math.ceil

/**
 * A [HorizontalAxis.ItemPlacer] for a time-of-day x-axis whose range is fixed to a calendar
 * period (e.g. a full day) rather than to the data's actual extent.
 */
internal class GbTimeAxisItemPlacer(private val minX: Double, private val maxX: Double) : HorizontalAxis.ItemPlacer {
    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = false

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val min = minX
        val max = maxX
        val range = max - min
        val spacing = getLabelSpacing(context, range, maxLabelWidth)
        if (range <= 0.0 || spacing <= 0.0) {
            return emptyList()
        }

        val values = mutableListOf<Double>()
        var value = min
        while (value <= max) {
            values += value
            value += spacing
        }
        // Avoid undershooting the exact max after repeated additions.
        if (values.isEmpty() || max - values.last() > spacing / 2) {
            values += max
        }
        return values
    }

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = listOf(minX, maxX)

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = getWidthMeasurementLabelValues(context, layerDimensions, fullXRange)

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float {
        val tickMargin = (tickThickness / 2f - layerDimensions.unscalableStartPadding).coerceAtLeast(0f)
        return maxLabelWidth / 2f + tickMargin + with(context) { EDGE_PADDING_DP.dp.pixels }
    }

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float {
        val tickMargin = (tickThickness / 2f - layerDimensions.unscalableEndPadding).coerceAtLeast(0f)
        return maxLabelWidth / 2f + tickMargin + with(context) { EDGE_PADDING_DP.dp.pixels }
    }

    private fun getLabelSpacing(
        context: CartesianDrawingContext,
        visibleRange: Double,
        maxLabelWidth: Float,
    ): Double {
        val labelWidth = maxLabelWidth.coerceAtLeast(REFERENCE_LABEL_WIDTH_PX)
        val targetLabelCount = (context.layerBounds.width / (labelWidth + MIN_LABEL_GAP_PX))
            .toInt()
            .coerceIn(MIN_TARGET_LABEL_COUNT, MAX_TARGET_LABEL_COUNT)
        return chooseTimeSpacing(visibleRange / targetLabelCount)
    }

    private fun chooseTimeSpacing(minimumSpacing: Double): Double {
        for (spacing in TIME_SPACING_SECONDS) {
            if (spacing >= minimumSpacing) {
                return spacing
            }
        }
        val days = ceil(minimumSpacing / DAY_SECONDS)
        return days * DAY_SECONDS
    }

    private companion object {
        private const val EDGE_PADDING_DP = 12f
        private const val REFERENCE_LABEL_WIDTH_PX = 44f
        private const val MIN_LABEL_GAP_PX = 28f
        private const val MIN_TARGET_LABEL_COUNT = 2
        private const val MAX_TARGET_LABEL_COUNT = 7
        private const val DAY_SECONDS = 24 * 60 * 60.0
        private val TIME_SPACING_SECONDS = doubleArrayOf(
            5 * 60.0,
            10 * 60.0,
            15 * 60.0,
            30 * 60.0,
            60 * 60.0,
            2 * 60 * 60.0,
            3 * 60 * 60.0,
            4 * 60 * 60.0,
            6 * 60 * 60.0,
            12 * 60 * 60.0,
            DAY_SECONDS,
        )
    }
}
