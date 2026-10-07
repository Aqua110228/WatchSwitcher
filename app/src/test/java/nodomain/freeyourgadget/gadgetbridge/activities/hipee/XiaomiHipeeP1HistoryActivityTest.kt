package nodomain.freeyourgadget.gadgetbridge.activities.hipee

import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1Reading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class XiaomiHipeeP1HistoryActivityTest {
    @Test
    fun aggregatesRawAndBankDirectionsWithoutDegreeThreshold() {
        val hour = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 9, 0)
        }.timeInMillis / 1000
        val data = XiaomiHipeeP1HistoryActivity.aggregate(
            listOf(
                reading(hour, forward = 1, bank = 0),
                reading(hour, forward = 0, bank = 0x81),
                reading(hour, forward = 0, bank = 0x01),
                reading(hour, forward = 0, bank = 0),
            ),
        )

        assertEquals(1, data.hours[9].forward)
        assertEquals(1, data.hours[9].left)
        assertEquals(1, data.hours[9].right)
        assertEquals(false, data.hours[9].hasOnlyZeroCorrections)
        assertEquals(4, data.hours[9].readingCount)
        assertEquals(1, data.forwardCount)
        assertEquals(1, data.leftCount)
        assertEquals(1, data.rightCount)
        assertEquals(3, data.totalPositiveMeasurements)
    }

    @Test
    fun retainsAnHourContainingOnlyZeroValuedReadings() {
        val hour = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 10, 0)
        }.timeInMillis / 1000
        val data = XiaomiHipeeP1HistoryActivity.aggregate(
            listOf(reading(hour, forward = 0, bank = 0)),
        )

        assertEquals(1, data.hours[10].readingCount)
        assertEquals(0, data.hours[10].forward)
        assertEquals(0, data.hours[10].left)
        assertEquals(0, data.hours[10].right)
        assertEquals(true, data.hours[10].hasOnlyZeroCorrections)
        assertEquals(0, data.totalPositiveMeasurements)
    }

    private fun reading(startupTime: Long, forward: Int, bank: Int) = XiaomiHipeeP1Reading(
        null, 1L, 1, 0, 0, startupTime, startupTime, forward, bank,
    )
}
