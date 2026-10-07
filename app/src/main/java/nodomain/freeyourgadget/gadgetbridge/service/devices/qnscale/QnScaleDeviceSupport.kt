/*  Copyright (C) 2026 brigon

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.qnscale

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.devices.GenericWeightSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.qnscale.QnScaleProtocol
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * BLE support for the "QN-Scale" family of Chipsea CS20-based body-composition scales (tested
 * against an Arboleaf CS20N). See [QnScaleProtocol] for the frame format.
 *
 * Unlike most Gadgetbridge devices, this handshake is scale-initiated rather than phone-initiated:
 * after notifications are enabled the scale itself sends a ScaleInfo frame unprompted, and every
 * later step (config -> ack -> time sync -> history trigger -> history query -> stored measurement)
 * happens as a reaction to whatever the scale just sent, not as a fixed up-front sequence. This
 * mirrors the actual order captured from the manufacturer's own app.
 */
class QnScaleDeviceSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private var weightScaleFactor: Float = 100.0f
    private var protocolType: Byte? = null

    init {
        addSupportedService(UUID_SERVICE_QN_SCALE)
    }

    override fun useAutoConnect(): Boolean {
        return false
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.notify(UUID_CHARACTERISTIC_NOTIFY, true)
        // Everything past this point (config, time sync, history sync) is driven reactively by
        // whatever the scale sends next - see onCharacteristicChanged.
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true
        }
        if (characteristic.uuid != UUID_CHARACTERISTIC_NOTIFY) {
            return false
        }

        when (val frame = QnScaleProtocol.parse(value, weightScaleFactor)) {
            is QnScaleProtocol.Frame.ScaleInfo -> {
                weightScaleFactor = frame.scaleFactor
                protocolType = frame.protocolType
                sendConfig()
            }

            is QnScaleProtocol.Frame.ConfigAck -> sendTimeSync()

            is QnScaleProtocol.Frame.HistoryTrigger -> sendHistoryQuery()

            is QnScaleProtocol.Frame.StoredMeasurement -> {
                val timestampMillis = QnScaleProtocol.qnEpochSecondsToMillis(frame.deviceEpochSeconds)
                if (frame.deviceEpochSeconds > lastImportedHistoryEpochSeconds()) {
                    saveMeasurement(frame.weightKg, frame.resistance1, timestampMillis)
                    saveLastImportedHistoryEpochSeconds(frame.deviceEpochSeconds)
                } else {
                    LOG.debug("Skipping already-imported stored measurement at {}", timestampMillis)
                }
            }

            is QnScaleProtocol.Frame.LiveWeight -> {
                if (frame.stable) {
                    saveMeasurement(frame.weightKg, frame.resistance1, System.currentTimeMillis())
                    sendStableAck()
                }
            }

            is QnScaleProtocol.Frame.Unknown -> LOG.debug("Unhandled QN-Scale opcode: 0x{}", Integer.toHexString(frame.opcode))
        }

        return true
    }

    override fun onSendConfiguration(config: String) {
        if (config == SettingsActivity.PREF_UNIT_WEIGHT && protocolType != null) {
            sendConfig()
        }
    }

    private fun sendConfig() {
        val type = protocolType ?: return
        val user = ActivityUser()
        val unitByte = when (GBApplication.getPrefs().weightUnit) {
            WeightUnit.POUND -> UNIT_BYTE_POUND
            else -> UNIT_BYTE_KILOGRAM
        }
        val frame = QnScaleProtocol.buildConfigFrame(
            protocolType = type,
            unitByte = unitByte,
            heightCm = user.heightCm,
            ageYears = user.age,
            isMale = user.gender == ActivityUser.GENDER_MALE,
        )
        createTransactionBuilder("send config").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendTimeSync() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildTimeSyncFrame(type, QnScaleProtocol.nowAsQnEpochSeconds())
        createTransactionBuilder("time sync").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendHistoryQuery() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildStoredDataQuery(type)
        createTransactionBuilder("history query").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendStableAck() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildStableAck(type)
        createTransactionBuilder("stable ack").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun saveMeasurement(weightKg: Float, resistanceOhm: Int, timestampMillis: Long) {
        try {
            GBApplication.acquireDB().use { handler ->
                val session = handler.daoSession
                val provider = GenericWeightSampleProvider(device, session)
                val sample = GenericWeightSample().apply {
                    timestamp = timestampMillis
                    this.weightKg = weightKg
                    // A raw resistance of 0 means the scale couldn't get a bioimpedance reading
                    // (typically poor foot contact) rather than a real measured impedance of 0Ω.
                    impedanceOhm = if (resistanceOhm > 0) resistanceOhm else null
                }
                provider.persistSamples(sample, context)
            }
            GB.signalActivityDataFinish(device)
        } catch (e: Exception) {
            LOG.error("Error saving QN-Scale measurement", e)
        }
    }

    private fun lastImportedHistoryEpochSeconds(): Long {
        return GBApplication.getDeviceSpecificSharedPrefs(device.address).getLong(PREF_LAST_HISTORY_EPOCH_SECONDS, 0L)
    }

    private fun saveLastImportedHistoryEpochSeconds(epochSeconds: Long) {
        GBApplication.getDeviceSpecificSharedPrefs(device.address)
            .edit()
            .putLong(PREF_LAST_HISTORY_EPOCH_SECONDS, epochSeconds)
            .apply()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(QnScaleDeviceSupport::class.java)

        private val UUID_SERVICE_QN_SCALE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        private const val PREF_LAST_HISTORY_EPOCH_SECONDS = "qnscale_last_history_epoch_seconds"

        private const val UNIT_BYTE_KILOGRAM: Byte = 0x01
        private const val UNIT_BYTE_POUND: Byte = 0x02
    }
}
