/*  Copyright (C) 2026 David Girón

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.nxwear.NxWearB8Coordinator
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2Sample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSample
import nodomain.freeyourgadget.gadgetbridge.entities.NxWearSportSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.TimeZone

/** BLE support for the Nx Wear B8. */
class NxWearB8Support : AbstractBTLESingleDeviceSupport(LOG) {
    @Volatile
    private var characteristics: Map<UUID, BluetoothGattCharacteristic> = emptyMap()
    private val historySync = NxWearHistorySync()
    private var liveActivityPacket: NxWearPacket? = null
    private val measurementTimeoutHandler = Handler(Looper.getMainLooper())
    private val measurementTimeout = Runnable {
        activeMeasurement?.let {
            val failed = !measurementHasResult
            cancelMeasurement(it)
            if (failed) reportMeasurementFailure()
        }
    }
    private val historyTimeoutHandler = Handler(Looper.getMainLooper())
    private val historyTimeout = Runnable { finishHistoryFetch() }
    private var historyFetchActive = false
    private var synchronizeCallStateAfterInitialization = false
    @Volatile
    private var activeMeasurement: Int? = null
    @Volatile
    private var measurementHasResult = false
    private var fetchAfterHistory = false
    override fun getCoordinator(): NxWearB8Coordinator = super.getCoordinator() as NxWearB8Coordinator

    init {
        addSupportedService(SERVICE_CHARACTERISTIC)
    }

    override fun useAutoConnect(): Boolean = true

    override fun getCharacteristic(uuid: UUID?): BluetoothGattCharacteristic? = uuid?.let { characteristics[it] }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
            characteristics = emptyMap()
        }
        super.onConnectionStateChange(gatt, status, newState)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt) {
        // Handle discovery here because the base implementation lets the BLE Intent API
        // subscribe to all discovered services. B8 uses only F618/B001 notifications.
        // This bypasses the API's read/write lookup, but preserves notification forwarding.
        characteristics = gatt.services
            .filter { it.uuid in getSupportedServices() }
            .flatMap { it.characteristics }
            .associateBy { it.uuid }
        if (getCharacteristic(RESPONSE_CHARACTERISTIC) == null || getCharacteristic(WRITE_CHARACTERISTIC) == null) {
            LOG.error("Nx Wear B8 measurement service is missing its notification/write characteristics")
            return
        }
        if (getDevice().state.compareTo(GBDevice.State.INITIALIZING) >= 0) return
        val builder = createTransactionBuilder("Initialize Nx Wear B8")
        initializeDevice(builder)
        if (getCoordinator().supportsConnectionPriority()) {
            builder.requestConnectionPriority(
                if (getDevicePrefs().getConnectionPriorityLowPower()) BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
                else BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
            )
        }
        builder.queue()
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        synchronizeCallStateAfterInitialization = false
        fetchAfterHistory = false
        finishHistoryFetch()
        restoreExerciseState()
        synchronizeCallStateAfterInitialization = true
        historySync.resetForInitialization()
        liveActivityPacket = null
        activeMeasurement = null
        measurementTimeoutHandler.removeCallbacks(measurementTimeout)
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.requestMtu(getCoordinator().getMtu())
        builder.notify(RESPONSE_CHARACTERISTIC, true)
        getCharacteristic(WRITE_CHARACTERISTIC)?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        builder.write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeInitializationBootstrap(buildBootstrap()))

        builder.setDeviceState(GBDevice.State.INITIALIZED)
        startHistoryFetch()
        return builder
    }

    override fun onSendConfiguration(config: String) {
        super.onSendConfiguration(config)
        if (config !in CONFIG_KEYS) return

        when (config) {
            NxWearB8Coordinator.ACTION_MEASURE_STRESS -> { startMeasurement(NxWearDataType.STRESS); return }
            NxWearB8Coordinator.ACTION_CANCEL_MEASURE_STRESS -> { cancelMeasurement(NxWearDataType.STRESS); return }
            NxWearB8Coordinator.ACTION_MEASURE_SPO2 -> { startMeasurement(NxWearDataType.SPO2); return }
            NxWearB8Coordinator.ACTION_CANCEL_MEASURE_SPO2 -> { cancelMeasurement(NxWearDataType.SPO2); return }
            NxWearB8Coordinator.ACTION_EXERCISE_START -> { setExerciseRunning(true); return }
            NxWearB8Coordinator.ACTION_EXERCISE_STOP -> { setExerciseRunning(false); return }
            NxWearB8Coordinator.PREF_SHAKE_TO_TAKE_PICTURE -> { writeShakeCameraSettings(); return }
        }
        val builder = createTransactionBuilder("Set Nx Wear B8 settings")
        if (config in HEALTH_CONFIG_KEYS) writeDetectionSettings(builder)
        if (config in WRIST_WAKE_CONFIG_KEYS) {
            if (config == NxWearB8Coordinator.PREF_LIFT_ENABLED) {
                val enabled = getDevicePrefs().getBoolean(NxWearB8Coordinator.PREF_LIFT_ENABLED, false)
                getDevicePrefs().preferences.edit()
                    .putString(DeviceSettingsPreferenceConst.PREF_ACTIVATE_DISPLAY_ON_LIFT, if (enabled) "scheduled" else "off")
                    .apply()
            }
            writeWristWakeSettings(builder)
        }
        if (config == DeviceSettingsPreferenceConst.PREF_TIMEFORMAT) {
            writeClockSettings(builder)
        }
        builder.queue()
    }

    @Synchronized
    private fun setExerciseRunning(starting: Boolean) {
        if (!isConnected()) return
        try {
            val store = NxWearWorkoutStore(getDevice())
            val now = System.currentTimeMillis()
            val workout = if (starting) {
                val type = getDevicePrefs()
                    .getString(NxWearB8Coordinator.PREF_EXERCISE_SPORT, NxWearSportSession.OUTDOOR_RUNNING.toString())
                    ?.toIntOrNull()
                    ?.takeIf { selected -> NxWearSportSession.SPORTS.any { it.type == selected } }
                    ?: NxWearSportSession.OUTDOOR_RUNNING
                store.start(type, now)
            } else {
                store.stop(now)
            }
            if (workout == null) {
                restoreExerciseState()
                return
            }
            getDevicePrefs().preferences.edit()
                .putBoolean(NxWearB8Coordinator.PREF_EXERCISE_RUNNING, starting).apply()
            // Persist the immutable session before sending a command: support recreation must not
            // replace its type with a later settings selection or lose its original start time.
            createTransactionBuilder(if (starting) "Start Nx Wear B8 exercise" else "Stop Nx Wear B8 exercise")
                .write(
                    WRITE_CHARACTERISTIC,
                    *NxWearSportSession.encode(workout.sportType,
                        if (starting) NxWearSportSession.START else NxWearSportSession.STOP,
                        workout.durationSeconds?.coerceAtMost(0xffff_ffffL) ?: 0L),
                ).queue()
            GB.signalActivityDataFinish(getDevice())
            if (!starting) {
                enrichWorkouts()
                if (historyFetchActive) fetchAfterHistory = true
                else onFetchRecordedData(RecordedDataTypes.TYPE_ACTIVITY)
            }
        } catch (e: Exception) {
            LOG.error("Unable to update Nx Wear B8 workout session", e)
        }
    }

    private fun restoreExerciseState() {
        try {
            val running = NxWearWorkoutStore(getDevice()).active() != null
            getDevicePrefs().preferences.edit()
                .putBoolean(NxWearB8Coordinator.PREF_EXERCISE_RUNNING, running).apply()
        } catch (e: Exception) {
            LOG.error("Unable to recover Nx Wear B8 workout session", e)
        }
    }

    private fun enrichWorkouts(packet: NxWearPacket? = null) {
        try {
            val timestamps = packet?.let {
                NxWearSportReading.parse(it)?.map { reading -> reading.timestampMillis }
                    ?: NxWearHeartRateReading.parse(it)?.map { reading -> reading.timestampMillis }
            }
            if (packet != null && timestamps.isNullOrEmpty()) return
            NxWearWorkoutStore(getDevice()).enrich(timestamps?.minOrNull() ?: 0,
                timestamps?.maxOrNull() ?: Long.MAX_VALUE)
            GB.signalActivityDataFinish(getDevice())
        } catch (e: Exception) {
            LOG.error("Unable to enrich Nx Wear B8 workouts", e)
        }
    }

    private fun writeShakeCameraSettings() {
        if (!isConnected()) return
        val enabled = getDevicePrefs().getBoolean(NxWearB8Coordinator.PREF_SHAKE_TO_TAKE_PICTURE, false)
        createTransactionBuilder("${if (enabled) "Enable" else "Disable"} Nx Wear B8 shake camera")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeShakeCamera(enabled))
            .queue()
    }

    override fun onFetchRecordedData(dataTypes: Int) {
        if (!isConnected() || dataTypes and RecordedDataTypes.TYPE_ACTIVITY == 0 ||
            !historySync.isComplete || historyFetchActive) return
        liveActivityPacket = null
        historySync.beginFetch()
        startHistoryFetch()
        // The history stream advances only after a complete data frame is acknowledged.
        createTransactionBuilder("Synchronize Nx Wear B8 history")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeInitializationBootstrap(buildBootstrap()))
            .queue()
    }

    override fun onFactoryReset() {
        if (!isConnected()) return
        createTransactionBuilder("Factory reset Nx Wear B8")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeFactoryReset())
            .queue()
    }

    private fun startHistoryFetch() {
        historyFetchActive = true
        getDevice().setBusyTask(R.string.busy_task_fetch_activity_data, getContext())
        getDevice().sendDeviceUpdateIntent(getContext())
        historyTimeoutHandler.postDelayed(historyTimeout, HISTORY_TIMEOUT_MS)
    }

    @Synchronized
    private fun finishHistoryFetch() {
        historyTimeoutHandler.removeCallbacks(historyTimeout)
        if (!historyFetchActive) return
        historyFetchActive = false
        historySync.finish()
        liveActivityPacket = null
        getDevice().unsetBusyTask()
        getDevice().sendDeviceUpdateIntent(getContext())
        GB.signalActivityDataFinish(getDevice())
        enrichWorkouts()
        if (synchronizeCallStateAfterInitialization) {
            synchronizeCallStateAfterInitialization = false
            synchronizeIdleCallState()
        }
        if (fetchAfterHistory && isConnected()) {
            fetchAfterHistory = false
            onFetchRecordedData(RecordedDataTypes.TYPE_ACTIVITY)
        }
    }

    private fun synchronizeIdleCallState() {
        if (!isConnected()) return
        val audio = getContext().getSystemService(AudioManager::class.java) ?: return
        if (audio.mode != AudioManager.MODE_NORMAL) return
        val telecom = getContext().getSystemService(TelecomManager::class.java) ?: return
        val isInCall = try {
            telecom.isInCall
        } catch (e: SecurityException) {
            LOG.debug("Cannot synchronize Nx Wear B8 call state without phone state permission")
            return
        }
        if (isInCall) return
        createTransactionBuilder("Synchronize Nx Wear B8 idle call state")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeCallEnded())
            .queue()
    }

    override fun disconnect() {
        fetchAfterHistory = false
        synchronizeCallStateAfterInitialization = false
        if (activeMeasurement != null && !measurementHasResult) reportMeasurementFailure()
        clearActiveMeasurement()
        finishHistoryFetch()
        super.disconnect()
        characteristics = emptyMap()
    }

    override fun dispose() {
        fetchAfterHistory = false
        synchronizeCallStateAfterInitialization = false
        measurementTimeoutHandler.removeCallbacks(measurementTimeout)
        finishHistoryFetch()
        super.dispose()
        characteristics = emptyMap()
    }

    override fun onSetTime() {
        if (!GBApplication.getPrefs().syncTime() || !isConnected()) return

        createTransactionBuilder("Synchronize Nx Wear B8 clock")
            .also { writeClockSettings(it) }
            .queue()
    }

    override fun onPowerOff() {
        createTransactionBuilder("Power off Nx Wear B8")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodePowerOff())
            .queue()
    }

    override fun onSetCallState(callSpec: CallSpec) {
        if (!isConnected()) return
        val packet = when (callSpec.command) {
            CallSpec.CALL_INCOMING -> NxWearProtocol.encodeIncomingCall(System.currentTimeMillis(), callSpec.name, callSpec.number)
            CallSpec.CALL_REJECT, CallSpec.CALL_END -> NxWearProtocol.encodeCallEnded()
            else -> return
        }
        createTransactionBuilder("Update Nx Wear B8 call state")
            .write(WRITE_CHARACTERISTIC, *packet)
            .queue()
    }

    override fun onHeartRateTest() {
        startManualHeartRate(false)
    }

    override fun onEnableRealtimeHeartRateMeasurement(enable: Boolean) {
        if (enable) startManualHeartRate(true) else stopManualHeartRate()
    }

    @Synchronized
    private fun startManualHeartRate(continuous: Boolean) {
        if (continuous && activeMeasurement == NxWearDataType.MANUAL_HEART_RATE) return
        if (!startMeasurement(NxWearDataType.MANUAL_HEART_RATE)) return
        if (!continuous) measurementTimeoutHandler.postDelayed(measurementTimeout, MEASUREMENT_TIMEOUT_MS)
    }

    @Synchronized
    private fun stopManualHeartRate() {
        if (activeMeasurement != NxWearDataType.MANUAL_HEART_RATE) return
        cancelMeasurement(NxWearDataType.MANUAL_HEART_RATE)
    }

    @Synchronized
    private fun startMeasurement(dataType: Int): Boolean {
        if (!isConnected()) {
            reportMeasurementFailure()
            return false
        }
        val builder = createTransactionBuilder("Start Nx Wear B8 measurement")
        activeMeasurement?.let {
            builder.write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeMeasurement(it, false))
        }
        measurementTimeoutHandler.removeCallbacks(measurementTimeout)
        activeMeasurement = dataType
        measurementHasResult = false
        builder.write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeMeasurement(dataType, true)).queue()
        if (dataType != NxWearDataType.MANUAL_HEART_RATE) {
            measurementTimeoutHandler.postDelayed(measurementTimeout, MEASUREMENT_TIMEOUT_MS)
        }
        return true
    }

    @Synchronized
    private fun cancelMeasurement(dataType: Int) {
        if (activeMeasurement == dataType) clearActiveMeasurement()
        if (!isConnected()) return
        createTransactionBuilder("Cancel Nx Wear B8 measurement")
            .write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeMeasurement(dataType, false))
            .queue()
    }

    @Synchronized
    private fun clearActiveMeasurement() {
        activeMeasurement = null
        measurementTimeoutHandler.removeCallbacks(measurementTimeout)
    }

    private fun reportMeasurementFailure() {
        val error = getContext().getString(R.string.nx_wear_b8_measurement_failed)
        publishMeasurementResult(error)
        GB.toast(getContext(), error, Toast.LENGTH_LONG, GB.WARN)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (characteristic.uuid == RESPONSE_CHARACTERISTIC) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            val packet = NxWearPacket.parse(value)
            val heartRate = if (historySync.isComplete) NxWearHeartRateReading.parse(packet) else null
            val spo2 = if (historySync.isComplete) NxWearSpo2Reading.parse(packet) else null
            val stress = NxWearStressReading.parse(packet)
            if (packet.raw.contentEquals(NxWearProtocol.SETTINGS_ACK)) {
                // This fixed response only acknowledges a settings write; it does not report stored settings.
                LOG.debug("Nx Wear B8 acknowledged a settings write")
            } else if (packet.raw.contentEquals(NxWearProtocol.INITIALIZATION_ACK)) {
                LOG.debug("Nx Wear B8 acknowledged its initialization request")
            } else if (packet.raw.contentEquals(NxWearProtocol.POWER_OFF_ACK)) {
                LOG.debug("Nx Wear B8 acknowledged its power-off request")
                disconnect()
            } else when {
                NxWearProtocol.isBatteryNotification(packet) -> {
                    handleBatteryNotification(packet)
                    acknowledgeMeasurementPacket(requireNotNull(packet.sequence), NxWearDataType.BATTERY)
                }
                NxWearProtocol.isShakeCameraEvent(packet) -> {
                    acknowledgeMeasurementPacket(requireNotNull(packet.sequence), NxWearDataType.SHAKE_CAMERA)
                    if (!getDevicePrefs().getBoolean(NxWearB8Coordinator.PREF_SHAKE_TO_TAKE_PICTURE, false)) {
                        LOG.debug("Ignoring Nx Wear B8 shake-to-camera event while disabled")
                        return true
                    }
                    LOG.debug("Received Nx Wear B8 shake-to-camera event")
                    val event = GBDeviceEventCameraRemote().apply {
                        this.event = GBDeviceEventCameraRemote.Event.TAKE_PICTURE
                    }
                    evaluateGBDeviceEvent(event)
                }
                heartRate != null &&
                    historySync.isComplete -> {
                        handleManualHeartRate(packet, heartRate)
                        if (packet.dataType == NxWearDataType.EXERCISE_HEART_RATE) enrichWorkouts(packet)
                    }
                spo2 != null &&
                    historySync.isComplete ->
                    handleSpo2Reading(packet, spo2, notifyResult = packet.dataType == NxWearDataType.SPO2)
                stress != null -> handleStressReading(packet, stress)
                packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x31 && packet.dataType == NxWearDataType.HEART_RATE_CONTROL &&
                    packet.isComplete && packet.payload.size == 1 -> {
                    acknowledgeMeasurementPacket(requireNotNull(packet.sequence), NxWearDataType.HEART_RATE_CONTROL)
                    // A null/closed control notification ends an unsuccessful measurement.
                    if (packet.payload[0] == 0.toByte() && activeMeasurement != null) {
                        val failed = !measurementHasResult
                        clearActiveMeasurement()
                        if (failed) reportMeasurementFailure()
                    }
                }
                packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x34 &&
                    packet.dataType in setOf(
                        NxWearDataType.SPO2, NxWearDataType.STRESS, NxWearDataType.MANUAL_HEART_RATE,
                        NxWearDataType.MESSAGE_NOTICE, NxWearDataType.CALL_CONTROL
                    )
                -> {
                    LOG.debug("Nx Wear B8 acknowledged command: {}", packet.dataType)
                }
                packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x31 && packet.dataType == NxWearDataType.DEVICE_LOG &&
                    historySync.isComplete -> handleLogFrame(packet)
                packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x31 && historySync.handlesLiveActivity(packet.dataType) -> handleLiveActivityFrame(packet)
                packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x31 && packet.dataType in NxWearProtocol.ACTIVITY_SUBTYPES -> {
                    handleInitializationActivityFrame(packet)
                }
                packet.kind == NxWearPacket.Kind.CONTINUATION -> {
                    if (historySync.hasPendingLog) handleLogContinuation(packet)
                    else if (liveActivityPacket != null) handleLiveActivityContinuation(packet)
                    else handleInitializationContinuation(packet)
                }
                NxWearProtocol.isCandidateSettingsReply(packet) -> {
                    LOG.debug("Received candidate Nx Wear B8 settings reply with sequence {}", packet.sequence)
                }
                else -> LOG.debug("Unexpected Nx Wear B8 response: {}", packet.raw.joinToString("") { "%02x".format(it) })
            }
            return true
        }
        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    private fun handleStressReading(packet: NxWearPacket, readings: List<NxWearStressReading>) {
        acknowledgeMeasurementPacket(requireNotNull(packet.sequence), NxWearDataType.STRESS)
        val valid = readings.filter { it.stress in 1..100 }
        if (valid.isEmpty()) return
        if (activeMeasurement == NxWearDataType.STRESS) {
            clearActiveMeasurement()
            publishMeasurementResult(getContext().getString(R.string.nx_wear_b8_stress_result, valid.last().stress))
        }
        try {
            withSampleIdentity { session, deviceId, userId ->
                val samples = valid.map { GenericStressSample(it.timestampMillis, deviceId, userId, it.stress) }
                GenericStressSampleProvider(getDevice(), session).addSamples(samples)
            }
            GB.signalActivityDataFinish(getDevice())
        } catch (e: Exception) {
            LOG.error("Unable to save Nx Wear B8 stress reading", e)
        }
    }

    private fun handleSpo2Reading(
        packet: NxWearPacket, readings: List<NxWearPacket.TimedReading>,
        acknowledge: Boolean = true, notifyResult: Boolean = true,
    ) {
        if (acknowledge) acknowledgeMeasurementPacket(requireNotNull(packet.sequence), requireNotNull(packet.dataType))
        val valid = readings.filter { it.value in 1..100 }
        if (valid.isEmpty()) return
        if (notifyResult && activeMeasurement == NxWearDataType.SPO2) {
            clearActiveMeasurement()
            publishMeasurementResult(getContext().getString(R.string.nx_wear_b8_spo2_result, valid.last().value))
        }
        try {
            withSampleIdentity { session, deviceId, userId ->
                val samples = valid.map { GenericSpo2Sample(it.timestampMillis, deviceId, userId, it.value) }
                GenericSpo2SampleProvider(getDevice(), session).addSamples(samples)
            }
            GB.signalActivityDataFinish(getDevice())
        } catch (e: Exception) {
            LOG.error("Unable to save Nx Wear B8 blood oxygen reading", e)
        }
    }

    private fun publishMeasurementResult(value: String) {
        val prefs = getDevicePrefs().preferences
        prefs.edit()
            .putString(NxWearB8Coordinator.PREF_MEASUREMENT_RESULT, value)
            .apply()
    }

    private fun acknowledgeMeasurementPacket(sequence: Int, dataType: Int) {
        historySync.acknowledge(sequence)
        if (historyFetchActive && dataType == NxWearDataType.DEVICE_LOG &&
            historySync.isComplete) {
            refreshHistoryTimeout(NxWearProtocol.historyTimeoutForSubtype(dataType))
        }
        createTransactionBuilder("Acknowledge Nx Wear B8 measurement data")
            .write(WRITE_CHARACTERISTIC, *NxWearPacket.ack(sequence, dataType))
            .queue()
    }

    private fun handleLogFrame(packet: NxWearPacket) {
        historySync.acceptLogFrame(packet)?.let { acknowledgeMeasurementPacket(it, NxWearDataType.DEVICE_LOG) }
    }

    private fun handleLogContinuation(packet: NxWearPacket) {
        historySync.acceptLogContinuation(packet)?.let { acknowledgeMeasurementPacket(it, NxWearDataType.DEVICE_LOG) }
    }

    private fun handleManualHeartRate(
        packet: NxWearPacket, readings: List<NxWearHeartRateReading>,
        acknowledge: Boolean = true, realtime: Boolean = true,
    ) {
        if (acknowledge) acknowledgeMeasurementPacket(requireNotNull(packet.sequence), requireNotNull(packet.dataType))
        val valid = readings.filter { HeartRateUtils.getInstance().isValidHeartRateValue(it.bpm) }
        if (valid.isNotEmpty()) {
            try {
                withSampleIdentity { session, deviceId, userId ->
                    val samples = valid.map { GenericHeartRateSample(it.timestampMillis, deviceId, userId, it.bpm) }
                    GenericHeartRateSampleProvider(getDevice(), session).addSamples(samples)
                }
                GB.signalActivityDataFinish(getDevice())
            } catch (e: Exception) {
                LOG.error("Unable to save Nx Wear B8 heart rate reading", e)
            }
        }
        if (!realtime || packet.dataType != NxWearDataType.HEART_RATE) return
        if (valid.isNotEmpty() && activeMeasurement == NxWearDataType.MANUAL_HEART_RATE) {
            measurementHasResult = true
        }
        // These notifications stream throughout the measurement; they are not completion events.
        // Keep the sensor running until the one-shot timeout or an explicit realtime stop.
        for (reading in valid) {
            val intent = Intent(DeviceService.ACTION_REALTIME_SAMPLES)
                .putExtra(GBDevice.EXTRA_DEVICE, getDevice())
                .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, reading as java.io.Serializable)
                .putExtra(DeviceService.EXTRA_TIMESTAMP, reading.timestampMillis)
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent)
        }
    }

    private fun handleInitializationActivityFrame(packet: NxWearPacket) {
        if (!historySync.acceptFrame(packet)) {
            LOG.debug("Unexpected Nx Wear B8 activity subtype {} during initialization step {}", packet.dataType, historySync.step)
            return
        }
        refreshHistoryTimeout()
        advanceInitializationWhenComplete()
    }

    private fun handleLiveActivityFrame(packet: NxWearPacket) {
        // Daily totals continue between history records. ACK them without extending the history
        // timeout: these live updates are not evidence that more stored records are pending.
        liveActivityPacket = packet
        if (packet.isComplete) completeLiveActivityPacket(packet)
    }

    private fun handleLiveActivityContinuation(continuation: NxWearPacket) {
        val first = liveActivityPacket ?: return
        val complete = first.append(continuation) ?: return
        liveActivityPacket = complete
        if (complete.isComplete) completeLiveActivityPacket(complete)
    }

    private fun completeLiveActivityPacket(packet: NxWearPacket) {
        liveActivityPacket = null
        if (packet.dataType == NxWearDataType.EXERCISE_HEART_RATE) {
            NxWearHeartRateReading.parse(packet)?.let {
                handleManualHeartRate(packet, it, acknowledge = false, realtime = false)
            }
        } else {
            persistSportPacket(packet)
        }
        acknowledgeMeasurementPacket(requireNotNull(packet.sequence), requireNotNull(packet.dataType))
        if (historySync.isComplete) enrichWorkouts(packet)
    }

    private fun withSampleIdentity(block: (DaoSession, Long, Long) -> Unit) {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val deviceId = requireNotNull(DBHelper.getDevice(getDevice(), session).id)
            val userId = requireNotNull(DBHelper.getUser(session).id)
            block(session, deviceId, userId)
        }
    }

    private fun persistSportPacket(packet: NxWearPacket) {
        val readings = NxWearSportReading.parse(packet) ?: return
        if (readings.isEmpty()) return
        try {
            withSampleIdentity { session, deviceId, userId ->
                for (reading in readings) {
                    session.nxWearSportSampleDao.insertOrReplace(NxWearSportSample(
                        reading.timestampMillis, deviceId, userId, reading.steps, reading.distance,
                        reading.calories, reading.duration,
                    ))
                }
            }
            GB.signalActivityDataFinish(getDevice())
        } catch (e: Exception) {
            LOG.error("Failed to persist Nx Wear B8 walking activity", e)
        }
    }

    private fun persistSleepPacket(packet: NxWearPacket) {
        val readings = NxWearSleepReading.parse(packet) ?: return
        if (readings.isEmpty()) return
        try {
            withSampleIdentity { session, deviceId, userId ->
                val samples = readings.map {
                    GenericSleepStageSample(it.timestampMillis, deviceId, userId, it.durationMinutes, it.kind.code)
                }
                GenericSleepStageSampleProvider(getDevice(), session).addSamples(samples)
            }
        } catch (e: Exception) {
            LOG.error("Unable to save Nx Wear B8 sleep history", e)
        }
    }

    private fun handleInitializationContinuation(packet: NxWearPacket) {
        if (!historySync.acceptContinuation(packet)) return
        refreshHistoryTimeout()
        advanceInitializationWhenComplete()
    }

    private fun advanceInitializationWhenComplete() {
        val completed = historySync.takeCompletedFrame() ?: return
        completed.deviceSyncPayload?.let { NxWearDeviceSync.parse(it)?.let(::applyDeviceSync) }
        completed.sportPacket?.let(::persistSportPacket)
        completed.historyPacket?.let { packet ->
            val heartRate = NxWearHeartRateReading.parse(packet)
            val spo2 = NxWearSpo2Reading.parse(packet)
            when {
                heartRate != null -> handleManualHeartRate(packet, heartRate, acknowledge = false, realtime = false)
                spo2 != null -> handleSpo2Reading(packet, spo2, acknowledge = false, notifyResult = false)
                packet.dataType == NxWearDataType.SLEEP -> persistSleepPacket(packet)
                packet.dataType == NxWearDataType.HISTORY_SPORT -> persistSportPacket(packet)
            }
        }
        createTransactionBuilder("Continue Nx Wear B8 initialization")
            .write(WRITE_CHARACTERISTIC, *completed.request)
            .queue()
        if (historyFetchActive) refreshHistoryTimeout(NxWearProtocol.historyTimeoutForSubtype(completed.acknowledgedType))
    }

    private fun refreshHistoryTimeout(timeoutMillis: Long = HISTORY_TIMEOUT_MS) {
        historyTimeoutHandler.removeCallbacks(historyTimeout)
        historyTimeoutHandler.postDelayed(historyTimeout, timeoutMillis)
    }

    private fun applyDeviceSync(sync: NxWearDeviceSync) {
        sync.firmwareVersion?.let {
            getDevice().setFirmwareVersion(it)
            getDevice().sendDeviceUpdateIntent(getContext())
        }
        if (sync.batteryLevel != null && sync.batteryStatus != null) {
            evaluateGBDeviceEvent(GBDeviceEventBatteryInfo().apply {
                level = sync.batteryLevel
                state = NxWearProtocol.batteryStateForStatus(sync.batteryStatus)
            })
        }
        val editor = getDevicePrefs().getPreferences().edit()
        sync.heartRateEnabled?.let { editor.putBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_AUTOMATIC_ENABLE, it) }
        sync.heartRateIntervalMinutes?.let { editor.putString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, (it * 60).toString()) }
        sync.spo2Enabled?.let { editor.putBoolean(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, it) }
        sync.wristWakeEnabled?.let {
            editor.putString(DeviceSettingsPreferenceConst.PREF_ACTIVATE_DISPLAY_ON_LIFT, if (it) "scheduled" else "off")
            editor.putBoolean(NxWearB8Coordinator.PREF_LIFT_ENABLED, it)
        }
        sync.wristWakeStart?.let { editor.putString(DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_START, it) }
        sync.wristWakeEnd?.let { editor.putString(DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_END, it) }
        sync.wristWakeDurationSeconds?.let { editor.putString(DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT, it.toString()) }
        editor.apply()
    }

    private fun handleBatteryNotification(packet: NxWearPacket) {
        val payload = packet.payload
        val batteryInfo = GBDeviceEventBatteryInfo().apply {
            level = payload[0].toInt() and 0xff
            state = NxWearProtocol.batteryStateForStatus(payload[1].toInt() and 0xff)
        }
        if (batteryInfo.state == BatteryState.UNKNOWN) {
            LOG.debug("Received Nx Wear B8 battery status {}, state mapping is unknown", payload[1].toInt() and 0xff)
        }
        evaluateGBDeviceEvent(batteryInfo)
    }

    private fun writeDetectionSettings(builder: TransactionBuilder) {
        val prefs = getDevicePrefs()
        val heartRateEnabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_AUTOMATIC_ENABLE, false)
        val spo2Enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, false)
        val intervalSeconds = prefs.getString(
            DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL,
            HeartRateCapability.MeasurementInterval.MINUTES_30.getIntervalSeconds().toString(),
        )?.toIntOrNull()
            ?.takeIf {
                it == HeartRateCapability.MeasurementInterval.MINUTES_30.getIntervalSeconds() ||
                    it == HeartRateCapability.MeasurementInterval.HOUR_1.getIntervalSeconds()
            }
            ?: HeartRateCapability.MeasurementInterval.MINUTES_30.getIntervalSeconds()
        val intervalMinutes = intervalSeconds / 60

        builder.write(
            WRITE_CHARACTERISTIC,
            *NxWearProtocol.encodeDetectionSettings(heartRateEnabled, intervalMinutes, spo2Enabled),
        )
    }

    private fun writeClockSettings(builder: TransactionBuilder) {
        val now = System.currentTimeMillis()
        builder.write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeClockSettings(now, TimeZone.getDefault().getOffset(now) / 1000, is24Hour()))
    }

    private fun is24Hour(): Boolean {
        val timeFormat = getDevicePrefs().getString(
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT,
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_AUTO,
        )
        return when (timeFormat) {
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_24H -> true
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_12H -> false
            else -> DateFormat.is24HourFormat(GBApplication.getContext())
        }
    }

    private fun buildBootstrap(): NxWearBootstrap {
        val user = ActivityUser()
        val now = System.currentTimeMillis()
        return NxWearBootstrap(
            userId = 0,
            isMale = user.gender == ActivityUser.GENDER_MALE,
            age = user.age.takeIf { it > 0 } ?: 20,
            heightCm = user.heightCm,
            weightKg = user.weightKg,
            epochSeconds = now / 1000L,
            timezoneOffsetSeconds = TimeZone.getDefault().getOffset(now) / 1000,
            is24Hour = is24Hour(),
            stepsGoal = user.stepsGoal,
            distanceGoalMeters = user.distanceGoalMeters,
            caloriesGoal = user.caloriesBurntGoal,
            sleepGoalSeconds = user.sleepDurationGoal * 60,
            activeTimeGoalSeconds = user.activeTimeGoalMinutes * 60,
        )
    }

    private fun writeWristWakeSettings(builder: TransactionBuilder) {
        val prefs = getDevicePrefs()
        val enabled = prefs.getBoolean(NxWearB8Coordinator.PREF_LIFT_ENABLED, false)
        val start = prefs.getString(
            DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_START, "09:00",
        ) ?: "00:00"
        val end = prefs.getString(
            DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_END, "23:59",
        ) ?: "00:00"
        val duration = prefs.getString(
            DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT, "5",
        )?.toIntOrNull()?.takeIf { it in 3..10 } ?: 5

        builder.write(WRITE_CHARACTERISTIC, *NxWearProtocol.encodeWristWakeSettings(enabled, start, end, duration))
    }

    companion object {
        private val SERVICE_CHARACTERISTIC = UUID.fromString("0000f618-0000-1000-8000-00805f9b34fb")
        private val WRITE_CHARACTERISTIC = UUID.fromString("0000b002-0000-1000-8000-00805f9b34fb")
        private val RESPONSE_CHARACTERISTIC = UUID.fromString("0000b001-0000-1000-8000-00805f9b34fb")
        private val HEALTH_CONFIG_KEYS = setOf(
            DeviceSettingsPreferenceConst.PREF_HEARTRATE_AUTOMATIC_ENABLE,
            DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING,
            DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL,
        )

        private val WRIST_WAKE_CONFIG_KEYS = setOf(
            NxWearB8Coordinator.PREF_LIFT_ENABLED,
            DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_START,
            DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_END,
            DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT,
        )

        private val CONFIG_KEYS = HEALTH_CONFIG_KEYS + setOf(
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT,
            NxWearB8Coordinator.PREF_SHAKE_TO_TAKE_PICTURE,
            NxWearB8Coordinator.ACTION_MEASURE_STRESS,
            NxWearB8Coordinator.ACTION_MEASURE_SPO2,
            NxWearB8Coordinator.ACTION_CANCEL_MEASURE_STRESS,
            NxWearB8Coordinator.ACTION_CANCEL_MEASURE_SPO2,
            NxWearB8Coordinator.ACTION_EXERCISE_START,
            NxWearB8Coordinator.ACTION_EXERCISE_STOP,
        ) + WRIST_WAKE_CONFIG_KEYS

        private const val MEASUREMENT_TIMEOUT_MS = 60_000L
        private const val HISTORY_TIMEOUT_MS = 60_000L
        private val LOG = LoggerFactory.getLogger(NxWearB8Support::class.java)
    }
}
