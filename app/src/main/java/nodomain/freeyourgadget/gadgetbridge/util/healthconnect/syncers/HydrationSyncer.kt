package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Volume
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectUtils
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.SyncException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Syncs the daily hydration totals to Health Connect, as one [HydrationRecord] for each day.
 */
internal object HydrationSyncer : HealthConnectSyncer {
    private val LOG: Logger = LoggerFactory.getLogger(HydrationSyncer::class.java)

    private const val RECORD_TYPE = "HydrationRecord"
    private const val MAX_VOLUME_ML = 100_000.0

    private data class DayTotal(
        val day: Int,
        val totalMl: Double,
        val lastEntryTimestamp: Long
    )

    override suspend fun sync(
        healthConnectClient: HealthConnectClient,
        gbDevice: GBDevice,
        metadata: Metadata,
        offset: ZoneId,
        sliceStartBoundary: Instant,
        sliceEndBoundary: Instant,
        grantedPermissions: Set<String>
    ): SyncerStatistics {
        val deviceName = gbDevice.aliasOrName

        if (HealthPermission.getWritePermission(HydrationRecord::class) !in grantedPermissions) {
            LOG.info("Skipping $RECORD_TYPE sync for device '$deviceName'; $RECORD_TYPE permission not granted.")
            return SyncerStatistics(recordType = RECORD_TYPE)
        }

        var latestEntryTimestamp: Long? = null

        // An entry in the slice changes the total of its day, so all the entries of that day are read.
        val dayTotals: List<DayTotal> = try {
            GBApplication.acquireDbReadOnly().use { dbInstance ->
                val provider = gbDevice.deviceCoordinator.getHydrationSampleProvider(gbDevice, dbInstance.daoSession)
                if (provider == null) {
                    LOG.info("$RECORD_TYPE sample provider not available for device '$deviceName'.")
                    return SyncerStatistics(recordType = RECORD_TYPE)
                }
                val sliceEntries = provider.getAllSamples(
                    sliceStartBoundary.toEpochMilli(),
                    sliceEndBoundary.toEpochMilli()
                )
                latestEntryTimestamp = sliceEntries.maxOfOrNull { it.timestamp }
                sliceEntries.map { it.day }.distinct().sorted().mapNotNull { day ->
                    val lastEntry = provider.getLastEntry(day) ?: return@mapNotNull null
                    DayTotal(day, provider.getDayTotal(day), lastEntry.timestamp)
                }
            }
        } catch (e: Exception) {
            throw SyncException(
                "Error fetching $RECORD_TYPE samples for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.",
                e
            )
        }

        if (dayTotals.isEmpty()) {
            LOG.info("No $RECORD_TYPE samples found by provider for device '$deviceName' in slice $sliceStartBoundary to $sliceEndBoundary.")
            return SyncerStatistics(recordType = RECORD_TYPE)
        }

        val recordVersion = System.currentTimeMillis()
        val recordsToInsert = mutableListOf<Record>()
        val clientRecordIdsToDelete = mutableListOf<String>()
        var skippedCount = 0

        for (dayTotal in dayTotals) {
            val date = LocalDate.of(dayTotal.day / 10000, dayTotal.day / 100 % 100, dayTotal.day % 100)
            val dayStart = date.atStartOfDay(offset).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(offset).toInstant().minusMillis(1)
            val recordMetadata = clientRecordMetadata(metadata, "hydration", dayTotal.day.toLong(), recordVersion)

            if (dayTotal.totalMl <= 0) {
                val clientRecordId = recordMetadata.clientRecordId
                if (clientRecordId == null) {
                    skippedCount++
                } else {
                    clientRecordIdsToDelete.add(clientRecordId)
                }
                continue
            }

            if (dayTotal.totalMl > MAX_VOLUME_ML || !dayTotal.totalMl.isFinite()) {
                LOG.skipOutOfRange(deviceName, "Hydration", dayTotal.totalMl, "0..$MAX_VOLUME_ML mL")
                skippedCount++
                continue
            }

            val endTime = Instant.ofEpochMilli(dayTotal.lastEntryTimestamp)
                .coerceIn(dayStart.plusSeconds(1), dayEnd)

            recordsToInsert.add(
                HydrationRecord(
                    startTime = dayStart,
                    startZoneOffset = offset.rules.getOffset(dayStart),
                    endTime = endTime,
                    endZoneOffset = offset.rules.getOffset(endTime),
                    volume = Volume.milliliters(dayTotal.totalMl),
                    metadata = recordMetadata
                )
            )
        }

        if (clientRecordIdsToDelete.isNotEmpty()) {
            LOG.info("Attempting to delete ${clientRecordIdsToDelete.size} $RECORD_TYPE(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")
            try {
                healthConnectClient.deleteRecords(
                    HydrationRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = clientRecordIdsToDelete
                )
            } catch (e: Exception) {
                throw SyncException(
                    "Error deleting $RECORD_TYPE(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.",
                    e
                )
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            LOG.info("Attempting to insert ${recordsToInsert.size} $RECORD_TYPE(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")
            HealthConnectUtils.insertRecords(recordsToInsert, healthConnectClient)
        }

        LOG.info("Synced ${recordsToInsert.size} and deleted ${clientRecordIdsToDelete.size} $RECORD_TYPE(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")
        return SyncerStatistics(
            recordsSynced = recordsToInsert.size + clientRecordIdsToDelete.size,
            recordsSkipped = skippedCount,
            recordType = RECORD_TYPE,
            latestRecordTimestamp = latestEntryTimestamp?.let { Instant.ofEpochMilli(it) }
        )
    }
}
