package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Pressure
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

internal object BloodPressureSyncer : AbstractTimeSampleSyncer<BloodPressureSample, BloodPressureRecord>() {
    override val logger: Logger = LoggerFactory.getLogger(BloodPressureSyncer::class.java)
    override val recordClass: KClass<BloodPressureRecord> = BloodPressureRecord::class

    override fun getSampleProvider(
        gbDevice: GBDevice,
        daoSession: DaoSession
    ): TimeSampleProvider<out BloodPressureSample>? {
        return gbDevice.deviceCoordinator.getBloodPressureSampleProvider(gbDevice, daoSession)
    }

    override fun convertSample(
        sample: BloodPressureSample,
        offset: ZoneOffset,
        metadata: Metadata,
        deviceName: String,
    ): BloodPressureRecord? {
        val systolic = sample.bpSystolic
        val diastolic = sample.bpDiastolic
        if (systolic !in 20..200) {
            logger.skipOutOfRange(deviceName, "BloodPressure systolic", systolic, "20..200 mmHg")
            return null
        }
        if (diastolic !in 10..180) {
            logger.skipOutOfRange(deviceName, "BloodPressure diastolic", diastolic, "10..180 mmHg")
            return null
        }
        return BloodPressureRecord(
            time = Instant.ofEpochMilli(sample.timestamp),
            zoneOffset = offset,
            metadata = clientRecordMetadata(metadata, "bp", sample.timestamp, System.currentTimeMillis()),
            systolic = Pressure.millimetersOfMercury(systolic.toDouble()),
            diastolic = Pressure.millimetersOfMercury(diastolic.toDouble())
        )
    }
}
