package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample

/**
 * Background-thread data access for one chart refresh.
 */
class ChartDataScope {
    /**
     * Runs [block] against a read-only DB handle, closed when [block] returns.
     */
    suspend fun <T> db(block: (DBHandler) -> T): T = withContext(Dispatchers.IO) {
        GBApplication.acquireDbReadOnly().use { block(it) }
    }

    /**
     * All activity samples for [device] between [tsStart] and [tsEnd] (epoch seconds, inclusive).
     */
    suspend fun activitySamples(device: GBDevice, tsStart: Int, tsEnd: Int): List<ActivitySample> =
        db { db ->
            val provider = device.deviceCoordinator.getSampleProvider(device, db.daoSession)
            provider?.getAllActivitySamples(tsStart, tsEnd) ?: emptyList()
        }
}
