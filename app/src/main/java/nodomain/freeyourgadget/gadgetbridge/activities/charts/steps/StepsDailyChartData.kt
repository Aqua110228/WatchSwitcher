package nodomain.freeyourgadget.gadgetbridge.activities.charts.steps

import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityAnalysis
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.SeriesStyle
import nodomain.freeyourgadget.gadgetbridge.model.ActivityAmount
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

object StepsDailyChartData {
    private const val Y_AXIS_HEADROOM_STEPS = 2000.0
    private const val SECONDS_PER_DAY = 24 * 60 * 60

    /**
     * This day's step and distance totals.
     */
    data class DailyTotals(val steps: Long, val distanceKm: Double)

    /**
     * Sums [samples] by activity kind (so periods like "not worn" are excluded).
     * Falls back to steps * step length when a device does not report distance.
     */
    fun buildDailyTotals(samples: Iterable<ActivitySample>): DailyTotals {
        val activityAmounts = ActivityAnalysis().calculateActivityAmounts(samples)
        var totalSteps = 0L
        var totalDistanceCm = 0L
        for (amount: ActivityAmount in activityAmounts.amounts) {
            if (amount.totalSteps > 0) {
                totalSteps += amount.totalSteps
            }
            if (amount.totalDistance > 0) {
                totalDistanceCm += amount.totalDistance
            }
        }
        var distanceCm = totalDistanceCm.toDouble()
        if (totalDistanceCm == 0L && totalSteps > 0) {
            // For gadgets that do not report distance, compute it from the steps.
            distanceCm = ActivityUser().stepLengthCm * totalSteps.toDouble()
        }
        return DailyTotals(steps = totalSteps, distanceKm = distanceCm / 100_000)
    }

    /**
     * The cumulative daily steps line, plus a dashed goal limit line.
     *
     * [dayStartTs] (the day's local midnight, epoch seconds) fixes the x-axis to the full day
     * regardless of how much of it actually has samples.
     */
    fun buildChartSpec(samples: List<ActivitySample>, dayStartTs: Int, stepsColor: Int, goal: Int): ChartSpec {
        if (samples.isEmpty()) {
            return ChartSpec.EMPTY
        }

        var sum = 0
        val points = ArrayList<ChartPoint>(samples.size + 1)
        // Anchor the line at day start, ensuring there's no gap when there are no records..
        points.add(ChartPoint(x = dayStartTs.toDouble(), y = 0.0))
        for (sample in samples) {
            if (sample.steps > 0) {
                sum += sample.steps
            }
            points.add(ChartPoint(x = sample.timestamp.toDouble(), y = sum.toDouble()))
        }

        val maxY = maxOf(points.maxOf { it.y }, goal.toDouble()) + Y_AXIS_HEADROOM_STEPS

        return ChartSpec(
            series = listOf(
                ChartSeries(
                    key = "steps",
                    label = "",
                    points = points,
                    style = SeriesStyle.Line(color = stepsColor, filled = true),
                ),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = dayStartTs.toDouble(),
                maximum = (dayStartTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = maxY),
            limitLines = listOf(LimitLineSpec(value = goal.toDouble(), color = stepsColor)),
        )
    }
}
