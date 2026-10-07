package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import nodomain.freeyourgadget.gadgetbridge.activities.charts.StressFragment
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

/**
 * Shared data model for the stress widgets.
 */
data class StressData(val value: Int, val ranges: IntArray, val totalTime: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StressData) return false

        if (value != other.value) return false
        if (!ranges.contentEquals(other.ranges)) return false
        if (!totalTime.contentEquals(other.totalTime)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + ranges.contentHashCode()
        result = 31 * result + totalTime.contentHashCode()
        return result
    }
}

private val LOG = LoggerFactory.getLogger("StressData")

/**
 * Computes the average stress for [scope]'s devices/period, plus a per-type time breakdown.
 * Returns null if no device in [scope] has any stress samples for the period.
 */
suspend fun computeStress(scope: WidgetDataScope): StressData? {
    var stressDevice: GBDevice? = null
    var averageStress = -1.0

    val totalTime = IntArray(StressFragment.StressType.entries.size)

    try {
        for (dev in scope.devices) {
            val samples = scope.db { db ->
                dev.deviceCoordinator.getStressSampleProvider(dev, db.daoSession)?.getAllSamples(
                    scope.query.timeFrom * 1000L,
                    scope.query.timeTo * 1000L
                )
            }
            if (samples.isNullOrEmpty()) {
                continue
            }

            stressDevice = dev
            val stressRanges = dev.deviceCoordinator.stressRanges
            var sum = 0
            for (sample in samples) {
                val stress = sample.stress
                sum += stress
                val stressType = StressFragment.StressType.fromStress(stress, stressRanges)
                if (stressType != StressFragment.StressType.UNKNOWN) {
                    totalTime[stressType.ordinal - 1] += 60
                }
            }
            averageStress = sum.toDouble() / samples.size
        }
    } catch (e: Exception) {
        LOG.error("Could not compute stress", e)
    }

    val device = stressDevice ?: return null
    return StressData(
        value = averageStress.roundToInt(),
        ranges = device.deviceCoordinator.stressRanges,
        totalTime = totalTime,
    )
}
