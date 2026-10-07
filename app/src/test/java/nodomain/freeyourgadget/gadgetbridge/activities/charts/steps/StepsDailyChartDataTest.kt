package nodomain.freeyourgadget.gadgetbridge.activities.charts.steps

import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert
import org.junit.Test

class StepsDailyChartDataTest : TestBase() {
    private val startTs = 1_000_000

    @Test
    fun `empty day has zero totals and an empty chart`() {
        val totals = StepsDailyChartData.buildDailyTotals(emptyList())
        Assert.assertEquals(0L, totals.steps)
        Assert.assertEquals(0.0, totals.distanceKm, 0.001)

        val spec = StepsDailyChartData.buildChartSpec(emptyList(), dayStartTs = startTs, stepsColor = 0, goal = 10_000)
        Assert.assertTrue(spec.isEmpty)
    }

    @Test
    fun `totals sum steps and estimate distance from step length`() {
        val samples = listOf(
            sample(startTs, steps = 100, distanceCm = -1),
            sample(startTs + 60, steps = 200, distanceCm = -1),
        )

        val totals = StepsDailyChartData.buildDailyTotals(samples)

        Assert.assertEquals(300L, totals.steps)
        Assert.assertTrue("distance should be estimated from steps", totals.distanceKm > 0.0)
    }

    @Test
    fun `totals prioritize reported distance over the step-length estimate`() {
        val samples = listOf(
            sample(startTs, steps = 100, distanceCm = 8_000),
            sample(startTs + 60, steps = 0, distanceCm = 0),
        )

        val totals = StepsDailyChartData.buildDailyTotals(samples)

        Assert.assertEquals(100L, totals.steps)
        Assert.assertEquals(0.08, totals.distanceKm, 0.001)
    }

    @Test
    fun `chart is anchored at zero steps at day start`() {
        val samples = listOf(sample(startTs + 4 * 60 * 60, steps = 50, distanceCm = -1))

        val spec = StepsDailyChartData.buildChartSpec(samples, dayStartTs = startTs, stepsColor = 0, goal = 10_000)

        val points = spec.series[0].points
        Assert.assertEquals(startTs.toDouble(), points.first().x, 0.001)
        Assert.assertEquals(0.0, points.first().y, 0.001)
    }

    @Test
    fun `chart accumulates steps over the day and ignores negative values`() {
        val samples = listOf(
            sample(startTs, steps = 50, distanceCm = -1),
            sample(startTs + 60, steps = 0, distanceCm = -1),
            sample(startTs + 120, steps = ActivitySample.NOT_MEASURED, distanceCm = -1),
            sample(startTs + 180, steps = 25, distanceCm = -1),
        )

        val spec = StepsDailyChartData.buildChartSpec(
            samples,
            dayStartTs = startTs,
            stepsColor = 0xFF0000,
            goal = 10_000
        )

        Assert.assertEquals(1, spec.series.size)
        val points = spec.series[0].points
        Assert.assertEquals(5, points.size)
        Assert.assertEquals(0.0, points[0].y, 0.001)
        Assert.assertEquals(50.0, points[1].y, 0.001)
        Assert.assertEquals(50.0, points[2].y, 0.001)
        Assert.assertEquals(50.0, points[3].y, 0.001)
        Assert.assertEquals(75.0, points[4].y, 0.001)
    }

    @Test
    fun `chart y axis maximum accounts for the goal, even when steps fall short`() {
        val samples = listOf(sample(startTs, steps = 100, distanceCm = -1))

        val spec = StepsDailyChartData.buildChartSpec(samples, dayStartTs = startTs, stepsColor = 0, goal = 10_000)

        Assert.assertEquals(1, spec.limitLines.size)
        Assert.assertEquals(10_000.0, spec.limitLines[0].value, 0.001)
        Assert.assertTrue(spec.yAxis.maximum!! >= 10_000.0)
    }

    @Test
    fun `chart x axis is fixed to the full day, regardless of samples`() {
        val samples = listOf(sample(startTs, steps = 100, distanceCm = -1))

        val spec = StepsDailyChartData.buildChartSpec(samples, dayStartTs = startTs, stepsColor = 0, goal = 10_000)

        Assert.assertEquals(startTs.toDouble(), spec.xAxis.minimum)
        Assert.assertEquals(startTs + 24 * 60 * 60.0, spec.xAxis.maximum)
    }

    private fun sample(timestamp: Int, steps: Int, distanceCm: Int): ActivitySample =
        MockSample(timestamp, steps, distanceCm)

    private class MockSample(
        private val timestamp: Int,
        private val steps: Int,
        private val distanceCm: Int,
    ) : ActivitySample {
        override fun getTimestamp(): Int = timestamp
        override fun getProvider(): SampleProvider<*>? = null
        override fun getRawKind(): Int = ActivityKind.ACTIVITY.code
        override fun getKind(): ActivityKind = ActivityKind.ACTIVITY
        override fun getRawIntensity(): Int = ActivitySample.NOT_MEASURED
        override fun getIntensity(): Float = 0f
        override fun getSteps(): Int = steps
        override fun getDistanceCm(): Int = distanceCm
        override fun getActiveCalories(): Int = ActivitySample.NOT_MEASURED
        override fun getHeartRate(): Int = ActivitySample.NOT_MEASURED
        override fun setHeartRate(value: Int) {}
    }
}