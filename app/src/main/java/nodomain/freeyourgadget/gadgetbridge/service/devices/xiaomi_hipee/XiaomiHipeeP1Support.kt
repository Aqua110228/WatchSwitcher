/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi_hipee

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1Reading
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1ReadingDao
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.util.GB
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import org.slf4j.LoggerFactory
import java.util.UUID

class XiaomiHipeeP1Support : AbstractBTLESingleDeviceSupport(LOG) {
    private val batteryPollHandler = Handler(Looper.getMainLooper())
    private val postureHandler = Handler(Looper.getMainLooper())
    private var lastLivePostureUpdateMs = 0L
    private var awaitingBindingConfirmation = false
    private var awaitingBatteryForReminderSettingsRead = false
    private var awaitingReminderSettings = false
    private var awaitingTimestampAcknowledgement = false
    private var synchronizeActivityAfterConnection = false
    private var readingSyncActive = false
    private var standardPostureCalibrationActive = false
    private var writeConfigurationAfterRead = false
    private val readingPage = mutableListOf<P1ReadingRecord>()
    private val standardPostureRunnable = Runnable {
        if (!standardPostureCalibrationActive) return@Runnable
        if (isConnected()) {
            updateStandardPostureStatus(STANDARD_POSTURE_STATUS_IN_PROGRESS)
            createTransactionBuilder("Set standard P1 posture")
                .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x32))
                .queue()
        } else {
            standardPostureCalibrationActive = false
            updateStandardPostureStatus(STANDARD_POSTURE_STATUS_CANCELLED)
        }
    }
    private val reminderConfigurationWriteRunnable = Runnable {
        if (writeConfigurationAfterRead && isConnected()) {
            writeConfigurationAfterRead = false
            writeReminderSettings()
        }
    }
    private val readingSyncTimeoutRunnable = Runnable {
        if (!readingSyncActive) return@Runnable

        LOG.warn("Timed out waiting for Xiaomi Hipee P1 stored-data transfer")
        readingSyncActive = false
        readingPage.clear()
        if (isConnected()) {
            // Best-effort close; no response is required after the local timeout.
            requestReadingPage(0x00)
        }
        finishActivityFetch()
    }
    private val batteryPollRunnable = object : Runnable {
        override fun run() {
            if (!isConnected()) return

            createTransactionBuilder("Poll P1 battery")
                .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x44))
                .queue()
            batteryPollHandler.postDelayed(this, BATTERY_POLL_INTERVAL_MS)
        }
    }

    init {
        addSupportedService(UUID_SERVICE_P1)
    }

    override fun useAutoConnect(): Boolean = true

    private fun prepareToDisconnect() {
        batteryPollHandler.removeCallbacks(batteryPollRunnable)
        postureHandler.removeCallbacksAndMessages(null)
        if (standardPostureCalibrationActive) {
            standardPostureCalibrationActive = false
            updateStandardPostureStatus(STANDARD_POSTURE_STATUS_CANCELLED)
        }
        awaitingBatteryForReminderSettingsRead = false
        awaitingReminderSettings = false
        awaitingTimestampAcknowledgement = false
        synchronizeActivityAfterConnection = false
        writeConfigurationAfterRead = false
        readingSyncActive = false
        readingPage.clear()
        postureHandler.removeCallbacks(readingSyncTimeoutRunnable)
        if (getDevice().isBusy) {
            getDevice().unsetBusyTask()
            getDevice().sendDeviceUpdateIntent(getContext())
        }
    }

    override fun disconnect() {
        prepareToDisconnect()
        super.disconnect()
    }

    override fun dispose() {
        prepareToDisconnect()
        super.dispose()
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        awaitingBindingConfirmation = false
        awaitingBatteryForReminderSettingsRead = false
        awaitingReminderSettings = false
        awaitingTimestampAcknowledgement = false
        synchronizeActivityAfterConnection = false
        writeConfigurationAfterRead = false
        readingSyncActive = false
        readingPage.clear()
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.notify(UUID_NOTIFY, true)
        if (isPaired()) {
            // When already bound, connect with 0x03.
            awaitingBindingConfirmation = true
            // The paired-device handshake must complete before either sync command is sent.
            // `0x04` then gates time sync, and its `0x06` acknowledgement gates activity sync.
            synchronizeActivityAfterConnection = true
            builder.writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x03))
            builder.setDeviceState(GBDevice.State.AUTHENTICATING)
        } else {
            // The initial binding command makes the P1 vibrate and user must press its button.
            builder.writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x01))
            builder.setDeviceState(GBDevice.State.AUTHENTICATING)
            GB.toast(getContext(), R.string.mi_hipee_p1_pairing_confirmation, Toast.LENGTH_LONG, GB.INFO)
        }
        return builder
    }

    private fun isPaired(): Boolean = getDevice().firmwareVersion != null

    private fun finishConnection() {
        // Live posture is session-only: a reconnect must never resume this function.
        getDevicePrefs().getPreferences().edit().putBoolean(PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE, false).apply()
        lastLivePostureUpdateMs = 0L

        // Request battery status
        awaitingBatteryForReminderSettingsRead = true
        createTransactionBuilder("Finish P1 connection")
            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x44))
            .setDeviceState(GBDevice.State.INITIALIZED)
            .queue()

        batteryPollHandler.removeCallbacks(batteryPollRunnable)
        batteryPollHandler.postDelayed(batteryPollRunnable, BATTERY_POLL_INTERVAL_MS)

        if (synchronizeActivityAfterConnection) {
            synchronizeActivityAfterConnection = false
            postureHandler.postDelayed(
                { onFetchRecordedData(RecordedDataTypes.TYPE_ACTIVITY) },
                100,
            )
        }
    }

    override fun onSendConfiguration(config: String) {
        when (config) {
            PREF_XIAOMI_HIPEE_P1_SET_STANDARD_POSTURE -> {
                standardPostureCalibrationActive = true
                updateStandardPostureStatus(STANDARD_POSTURE_STATUS_RUNNING)
                postureHandler.removeCallbacks(standardPostureRunnable)
                postureHandler.postDelayed(standardPostureRunnable, STANDARD_POSTURE_DELAY_MS)
            }
            PREF_XIAOMI_HIPEE_P1_CANCEL_STANDARD_POSTURE -> {
                standardPostureCalibrationActive = false
                postureHandler.removeCallbacks(standardPostureRunnable)
                updateStandardPostureStatus(STANDARD_POSTURE_STATUS_CANCELLED)
            }
            PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE -> {
                val livePostureEnabled = getDevicePrefs().getBoolean(PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE, false)
                if (livePostureEnabled) {
                    lastLivePostureUpdateMs = 0L
                }
                createTransactionBuilder(if (livePostureEnabled) "Start live P1 posture" else "Stop live P1 posture")
                    .writeLegacy(
                        getCharacteristic(UUID_WRITE),
                        *encodeCommand(0x34, 0x00, 0x14, if (livePostureEnabled) 0x01 else 0x00),
                    )
                    .queue()
            }
            PREF_VIBRATION_ENABLE,
            PREF_XIAOMI_HIPEE_P1_LONG_VIBRATION,
            PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE,
            PREF_XIAOMI_HIPEE_P1_EXERCISE_REMINDER_ANGLE,
            PREF_XIAOMI_HIPEE_P1_SEDENTARY_REMINDER,
            PREF_XIAOMI_HIPEE_P1_DELAY_REMINDER,
            PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL -> applyReminderSettings()
            else -> super.onSendConfiguration(config)
        }
    }

    override fun onFetchRecordedData(dataTypes: Int) {
        // GBAutoFetchReceiver requests TYPE_SYNC, which includes TYPE_ACTIVITY.
        if (!isConnected() || dataTypes and RecordedDataTypes.TYPE_ACTIVITY == 0) return
        if (readingSyncActive) {
            LOG.debug("Ignoring overlapping Xiaomi Hipee P1 stored-data fetch")
            return
        }

        getDevice().setBusyTask(R.string.busy_task_fetch_activity_data, getContext())
        getDevice().sendDeviceUpdateIntent(getContext())

        // 0x36/03 starts a new session.
        // 0x36/02 is post-upload page acknowledgement.
        readingSyncActive = true
        readingPage.clear()
        postureHandler.removeCallbacks(readingSyncTimeoutRunnable)
        postureHandler.postDelayed(readingSyncTimeoutRunnable, READING_SYNC_TIMEOUT_MS)
        requestReadingPage(0x03)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (characteristic.uuid != UUID_NOTIFY) {
            return super.onCharacteristicChanged(gatt, characteristic, value)
        }
        if (LOG.isDebugEnabled) {
            LOG.debug("P1 notification: {}", value.joinToString(":") { "%02x".format(it) })
        }
        val payload = decodeCommand(value) ?: return true
        when (payload.command) {
            0x02.toByte() -> {
                if (!awaitingBindingConfirmation && payload.size == 1 && payload[0] == 0x01.toByte()) {
                    awaitingBindingConfirmation = true
                    createTransactionBuilder("Complete P1 pairing")
                        .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x03))
                        .queue()
                }
            }
            0x04.toByte() -> {
                if (awaitingBindingConfirmation && isBindingConfirmation(payload)) {
                    awaitingBindingConfirmation = false
                    parseDeviceStatus(payload)?.let { status ->
                        // FW2 came from generic BLE module's DIS Software Revision, not P1. Ignore.
                        getDevice().setFirmwareVersion2(null)
                        evaluateGBDeviceEvent(GBDeviceEventVersionInfo().also { it.fwVersion = status.firmwareVersion })
                        evaluateGBDeviceEvent(
                            GBDeviceEventUpdateDeviceInfo("FREE STORAGE: ", "${status.freeStoragePercent}%"),
                        )

                        evaluateGBDeviceEvent(GBDeviceEventBatteryInfo().also { it.level = status.batteryLevel })
                    }
                    if (GBApplication.getPrefs().syncTime()) {
                        // Immediately returns this 0x05 write.
                        // The device acknowledges it with 0x06.
                        awaitingTimestampAcknowledgement = true
                        createTransactionBuilder("Synchronize P1 time")
                            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeTimestamp(System.currentTimeMillis() / 1000L))
                            .queue()
                    } else {
                        finishConnection()
                    }
                }
            }
            0x06.toByte() -> {
                if (awaitingTimestampAcknowledgement && parseTimestampAcknowledgement(payload) != null) {
                    awaitingTimestampAcknowledgement = false
                    finishConnection()
                }
            }
            0x45.toByte() -> parseChargingStatus(payload)?.let { status ->
                // `09 03 45 [percentage] [charging status] [checksum]` is the regular battery response.
                evaluateGBDeviceEvent(GBDeviceEventBatteryInfo().also {
                    it.level = status.level
                    it.state = status.state
                })
                requestReminderSettingsAfterBatteryResponse()
            }
            0x47.toByte() -> parseReminderSettings(payload)?.let { settings ->
                if (writeConfigurationAfterRead) {
                    postureHandler.removeCallbacks(reminderConfigurationWriteRunnable)
                    postureHandler.postDelayed(reminderConfigurationWriteRunnable, DOUBLE_REMINDER_DELAY_MS)
                } else {
                    getDevicePrefs().getPreferences().edit()
                        .putBoolean(PREF_XIAOMI_HIPEE_P1_LONG_VIBRATION, settings.longVibration)
                        .putString(PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE, settings.angleDegrees.toString())
                        .putString(PREF_XIAOMI_HIPEE_P1_EXERCISE_REMINDER_ANGLE, settings.exerciseAngleDegrees.toString())
                        .putString(PREF_XIAOMI_HIPEE_P1_SEDENTARY_REMINDER, settings.sedentaryMinutes.toString())
                        .putString(PREF_XIAOMI_HIPEE_P1_DELAY_REMINDER, settings.delaySeconds.toString())
                        .putBoolean(PREF_VIBRATION_ENABLE, !settings.vibrationDisabled)
                        .apply()
                    stopLivePostureAfterReminderSettingsResponse()
                }
            }
            0x51.toByte() -> parseDoubleReminderStatus(payload)?.let { enabled ->
                if (!enabled) {
                    // The device only reports the enabled flag here. Do not retain a stale
                    // interval when it explicitly reports that the reminder is disabled.
                    getDevicePrefs().getPreferences().edit()
                        .putString(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL, "0")
                        .apply()
                }
            }
            0x35.toByte() -> {
                if (getDevicePrefs().getBoolean(PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE, false)) {
                    parseLivePosture(payload)?.let { incline ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastLivePostureUpdateMs >= LIVE_POSTURE_UPDATE_INTERVAL_MS) {
                            lastLivePostureUpdateMs = now
                            evaluateGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("POSTURE: ", "%.2f°".format(incline)))
                        }
                    }
                }
            }
            0x33.toByte() -> parseStandardPostureResult(payload)?.let { result ->
                if (result.success) {
                    standardPostureCalibrationActive = false
                    updateStandardPostureStatus(STANDARD_POSTURE_STATUS_SUCCESS)
                } else if (standardPostureCalibrationActive) {
                    updateStandardPostureStatus("$STANDARD_POSTURE_STATUS_RETRY_PREFIX${result.code}")
                    postureHandler.removeCallbacks(standardPostureRunnable)
                    postureHandler.postDelayed(standardPostureRunnable, STANDARD_POSTURE_DELAY_MS)
                }
            }
            0x37.toByte() -> processReadingPage(payload)
        }
        return true
    }

    override fun onSetTime() {
        if (!GBApplication.getPrefs().syncTime() || !isConnected()) return

        createTransactionBuilder("Synchronize P1 time")
            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeTimestamp(System.currentTimeMillis() / 1000L))
            .queue()
    }

    private fun updateStandardPostureStatus(status: String) {
        getDevicePrefs().getPreferences().edit()
            .putString(PREF_XIAOMI_HIPEE_P1_STANDARD_POSTURE_STATUS, status)
            .apply()
    }

    /** Writes reminder configuration and its preceding 0x50. */
    private fun applyReminderSettings() {
        if (getFirmwareVersion() > 26) {
            writeConfigurationAfterRead = true
            postureHandler.removeCallbacks(doubleReminderWriteRunnable)
            createTransactionBuilder("Read P1 reminder settings before write")
                .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x46))
                .queue()
            postureHandler.postDelayed(doubleReminderWriteRunnable, DOUBLE_REMINDER_DELAY_MS)
        } else {
            writeReminderSettings()
        }
    }

    private val doubleReminderWriteRunnable = Runnable {
        if (writeConfigurationAfterRead && isConnected()) {
            createTransactionBuilder("Set P1 double reminder")
                .writeLegacy(getCharacteristic(UUID_WRITE), *encodeDoubleReminder(getDoubleReminderIntervalMinutes()))
                .queue()
        }
    }

    private fun writeReminderSettings() {
        val reminderAngle = getDevicePrefs().getString(PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE, "10").toIntOrNull()
            ?.takeIf { it in REMINDER_ANGLES } ?: DEFAULT_REMINDER_ANGLE
        val reminderDelay = getDevicePrefs().getString(PREF_XIAOMI_HIPEE_P1_DELAY_REMINDER, "3").toIntOrNull()
            ?.takeIf { it in REMINDER_DELAYS } ?: DEFAULT_REMINDER_DELAY
        val exerciseReminderAngle = getDevicePrefs().getString(PREF_XIAOMI_HIPEE_P1_EXERCISE_REMINDER_ANGLE, "10").toIntOrNull()
            ?.takeIf { it in EXERCISE_REMINDER_ANGLES } ?: DEFAULT_EXERCISE_REMINDER_ANGLE
        val sedentaryReminderMinutes = getDevicePrefs().getString(PREF_XIAOMI_HIPEE_P1_SEDENTARY_REMINDER, "60").toIntOrNull()
            ?.takeIf { it in SEDENTARY_REMINDER_MINUTES } ?: DEFAULT_SEDENTARY_REMINDER_MINUTES
        val vibrationDisabled = getDevicePrefs().getBoolean(PREF_VIBRATION_ENABLE, true).not()
        val longVibration = getDevicePrefs().getBoolean(PREF_XIAOMI_HIPEE_P1_LONG_VIBRATION, false)
        createTransactionBuilder("Set P1 reminder settings")
            .writeLegacy(
                getCharacteristic(UUID_WRITE),
                *encodeReminderSettings(
                    reminderAngle,
                    reminderDelay,
                    vibrationDisabled,
                    longVibration,
                    exerciseReminderAngle,
                    sedentaryReminderMinutes,
                ),
            )
            .queue()
    }

    /**
     * The interval is the single source of truth: zero disables the device command. Migrate the
     * previous separate boolean only when no interval was persisted, then remove that obsolete key.
     */
    private fun getDoubleReminderIntervalMinutes(): Int {
        val preferences = getDevicePrefs().getPreferences()
        if (!preferences.contains(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL)) {
            val migratedInterval = if (preferences.getBoolean(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER, true)) {
                DEFAULT_DOUBLE_REMINDER_INTERVAL
            } else {
                0
            }
            preferences.edit()
                .putString(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL, migratedInterval.toString())
                .remove(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER)
                .apply()
            return migratedInterval
        }

        return preferences.getString(PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL, null)?.toIntOrNull()
            ?.takeIf { it == 0 || it in DOUBLE_REMINDER_INTERVALS }
            ?: DEFAULT_DOUBLE_REMINDER_INTERVAL
    }

    private fun getFirmwareVersion(): Int =
        getDevice().firmwareVersion?.removePrefix("V0.")?.toIntOrNull() ?: 0

    private fun requestReminderSettingsAfterBatteryResponse() {
        if (!awaitingBatteryForReminderSettingsRead) return

        awaitingBatteryForReminderSettingsRead = false
        awaitingReminderSettings = true
        createTransactionBuilder("Read P1 reminder settings")
            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x46))
            .queue()
    }

    private fun stopLivePostureAfterReminderSettingsResponse() {
        if (!awaitingReminderSettings) return

        awaitingReminderSettings = false
        createTransactionBuilder("Stop live P1 posture")
            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x34, 0x00, 0x14, 0x00))
            .queue()
    }

    /** Fetches the capture-backed stored-readings pages used by the control-center sync button. */
    private fun requestReadingPage(page: Int) {
        createTransactionBuilder("Read P1 stored readings")
            .writeLegacy(getCharacteristic(UUID_WRITE), *encodeCommand(0x36, page.toByte()))
            .queue()
    }

    private fun finishActivityFetch() {
        postureHandler.removeCallbacks(readingSyncTimeoutRunnable)
        GB.signalActivityDataFinish(getDevice())
        if (getDevice().isBusy) {
            getDevice().unsetBusyTask()
            getDevice().sendDeviceUpdateIntent(getContext())
        }
    }

    private fun processReadingPage(payload: P1Payload) {
        if (!readingSyncActive || payload.size != 15) return

        when (payload.uint(0)) {
            // 0x36/0x03 initializes transfer, the response in here 0x05 requests another page 0x36/0x01.
            0x05 -> requestReadingPage(0x01)
            // 0x04 terminates a page. Persist before acknowledging it: a failed write deliberately
            // leaves the device page unacknowledged, so the next sync can replay it safely.
            0x04 -> {
                if (readingPage.isNotEmpty() && !persistReadingPage(readingPage)) return
                readingPage.clear()
                requestReadingPage(0x02)
            }
            // 0x00 ends the transfer. It is a control packet, not a posture record.
            0x00 -> {
                requestReadingPage(0x00)
                readingSyncActive = false
                readingPage.clear()
                finishActivityFetch()
            }
            else -> parseReadingRecord(payload)?.let(readingPage::add)
        }
    }

    /**
     * Persists a completed device page atomically.
     * Timestamps are not unique across P1 record types.
     *
     * A failed transaction returns false, preventing the `0x36/02` acknowledgement
     * and preserving the device page for a safe replay on the next sync.
     */
    private fun persistReadingPage(records: List<P1ReadingRecord>): Boolean {
        return try {
            GBApplication.acquireDB().use { dbHandler: DBHandler ->
                val session = dbHandler.daoSession
                val deviceId = requireNotNull(DBHelper.getDevice(getDevice(), session).id)
                val dao = session.xiaomiHipeeP1ReadingDao
                session.runInTx {
                    records.forEach { record ->
                        // Angles are payload values, not identity fields; timestamp alone is not unique.
                        val exists = dao.queryBuilder()
                            .where(XiaomiHipeeP1ReadingDao.Properties.DeviceId.eq(deviceId))
                            .where(XiaomiHipeeP1ReadingDao.Properties.RecordType.eq(record.type))
                            .where(XiaomiHipeeP1ReadingDao.Properties.DataNum.eq(record.dataNum))
                            .where(XiaomiHipeeP1ReadingDao.Properties.DataCount.eq(record.dataCount))
                            .where(XiaomiHipeeP1ReadingDao.Properties.StartupTime.eq(record.startupTime))
                            .where(XiaomiHipeeP1ReadingDao.Properties.Timestamp.eq(record.time))
                            .count() != 0L
                        if (!exists) {
                            dao.insert(XiaomiHipeeP1Reading(null, deviceId, record.type, record.dataNum,
                                record.dataCount, record.startupTime, record.time, record.forwardAngle, record.bankAngle))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            LOG.error("Unable to persist P1 history page; not acknowledging device page", e)
            false
        }
    }

    companion object {
        /** Decoded byte-for-byte `0x37` row. Angles are raw unsigned device values, not degrees. */
        internal data class P1ReadingRecord(
            val type: Int,
            val dataNum: Int,
            val dataCount: Int,
            val startupTime: Long,
            val time: Long,
            val forwardAngle: Int,
            val bankAngle: Int,
            val leftAngle: Int,
            val rightAngle: Int,
        )

        /**
         * Control records 00, 04 and 05 are excluded; all other types are retained.
         */
        internal fun parseReadingRecord(payload: P1Payload): P1ReadingRecord? {
            if (payload.size != 15) return null
            val type = payload.uint(0)
            if (type == 0x00 || type == 0x04 || type == 0x05) return null
            val bankAngle = payload.uint(14)
            return P1ReadingRecord(
                type,
                payload.be16(1),
                payload.be16(3),
                payload.be32(5),
                payload.be32(9),
                payload.uint(13),
                bankAngle,
                if (bankAngle >= 128) bankAngle - 128 else 0,
                if (bankAngle < 128) bankAngle else 0)
        }

        private val LOG = LoggerFactory.getLogger(XiaomiHipeeP1Support::class.java)
        // On Wireshark ignore PKOC label as UUID, the raw ATT service UUID is 0xFFF0.
        private val UUID_SERVICE_P1 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_WRITE = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val UUID_NOTIFY = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private const val BATTERY_POLL_INTERVAL_MS = 10_000L
        private const val STANDARD_POSTURE_DELAY_MS = 3_000L
        private const val STANDARD_POSTURE_STATUS_RUNNING = "RUNNING"
        private const val STANDARD_POSTURE_STATUS_IN_PROGRESS = "IN_PROGRESS"
        private const val STANDARD_POSTURE_STATUS_SUCCESS = "SUCCESS"
        private const val STANDARD_POSTURE_STATUS_CANCELLED = "CANCELLED"
        private const val STANDARD_POSTURE_STATUS_RETRY_PREFIX = "RETRY:"
        private const val LIVE_POSTURE_UPDATE_INTERVAL_MS = 100L
        private const val DOUBLE_REMINDER_DELAY_MS = 300L
        private const val READING_SYNC_TIMEOUT_MS = 30_000L
        private const val DEFAULT_REMINDER_ANGLE = 10
        private const val DEFAULT_REMINDER_DELAY = 3
        private const val DEFAULT_EXERCISE_REMINDER_ANGLE = 10
        private const val DEFAULT_SEDENTARY_REMINDER_MINUTES = 60
        private const val DEFAULT_DOUBLE_REMINDER_INTERVAL = 30
        private val REMINDER_ANGLES = setOf(5, 10, 15, 20, 25, 30)
        private val REMINDER_DELAYS = setOf(0, 2, 3, 5, 10, 20)
        private val EXERCISE_REMINDER_ANGLES = setOf(5, 10, 15, 20)
        private val SEDENTARY_REMINDER_MINUTES = setOf(0, 60, 120, 180, 240)
        private val DOUBLE_REMINDER_INTERVALS = setOf(15, 30, 60, 120, 180)

        /**
         * Encodes an FFF2 command as `09 + payload length + payload + two's-complement checksum`.
         * The length counts only the bytes after it, excluding both the leading `09` and checksum.
         */
        internal fun encodeCommand(vararg payload: Byte): ByteArray {
            require(payload.size <= 0xff) { "P1 payload exceeds one-byte frame length" }
            val frame = byteArrayOf(0x09, payload.size.toByte()) + payload
            val checksum = (-frame.sumOf { it.toInt() and 0xff }) and 0xff
            return frame + checksum.toByte()
        }

        /** Encodes command 0x05's unsigned big-endian Unix timestamp. */
        internal fun encodeTimestamp(epochSeconds: Long): ByteArray {
            require(epochSeconds in 0..0xffff_ffffL) { "P1 timestamp outside unsigned 32-bit range: $epochSeconds" }
            return encodeCommand(
                0x05,
                (epochSeconds shr 24).toByte(),
                (epochSeconds shr 16).toByte(),
                (epochSeconds shr 8).toByte(),
                epochSeconds.toByte(),
            )
        }

        /** `09 05 06 [Unix timestamp BE32] [checksum]` acknowledges command 0x05. */
        internal fun parseTimestampAcknowledgement(payload: P1Payload): Long? =
            if (payload.size == 4) payload.be32(0) else null

        /**
         * Encodes the full 15-byte `0x30` reminder configuration, not an angle/delay-only write.
         * Byte indexes below exclude the command byte:
         *
         * - 0: shock mode (`00` long, `01` short); 1: shock intensity (0, 50, or 100),
         *      retained as the captured default.
         * - 2: forward/rest reminder angle; 3: lateral angle, both encoded in five-degree steps.
         * - 4-5 and 6-7: captured constants `01 f4` and `07 d0`; their meanings are unknown.
         * - 8-9: sedentary duration in big-endian seconds (`0e 10` is one hour; zero disables it).
         * - 10: inverted intelligent-reminder flag (`00` enabled); retained as the captured default.
         * - 11: reminder delay in seconds.
         * - 12: DND/vibration-disable flag (`00` enabled, `01` disabled).
         * - 13: walking/exercise angle in five-degree steps; 14: captured constant `0f`.
         *
         * Firmware newer than 26 additionally uses the separate, source-backed `0x50`
         * double-reminder command, encoded by [encodeDoubleReminder].
         */
        internal fun encodeReminderSettings(
            angleDegrees: Int,
            delaySeconds: Int,
            vibrationDisabled: Boolean,
            longVibration: Boolean = false,
            exerciseAngleDegrees: Int = DEFAULT_EXERCISE_REMINDER_ANGLE,
            sedentaryMinutes: Int = DEFAULT_SEDENTARY_REMINDER_MINUTES,
        ): ByteArray {
            require(angleDegrees in REMINDER_ANGLES) { "Unsupported P1 reminder angle: $angleDegrees" }
            require(delaySeconds in REMINDER_DELAYS) { "Unsupported P1 reminder delay: $delaySeconds" }
            require(exerciseAngleDegrees in EXERCISE_REMINDER_ANGLES) {
                "Unsupported P1 exercise reminder angle: $exerciseAngleDegrees"
            }
            require(sedentaryMinutes in SEDENTARY_REMINDER_MINUTES) {
                "Unsupported P1 sedentary reminder duration: $sedentaryMinutes"
            }
            val sedentarySeconds = sedentaryMinutes * 60
            return encodeCommand(
                0x30, if (longVibration) 0x00 else 0x01, 0x64, angleDegrees.toByte(), 0x0f,
                0x01, 0xf4.toByte(), 0x07, 0xd0.toByte(),
                (sedentarySeconds shr 8).toByte(), sedentarySeconds.toByte(),
                0x00, delaySeconds.toByte(), if (vibrationDisabled) 0x01 else 0x00,
                exerciseAngleDegrees.toByte(), 0x0f,
            )
        }

        /**
         * Encodes firmware >26's `0x50` double reminder as `[enabled, 00, minutes BE16, 01]`.
         * Zero minutes is the disabled selection; every supported nonzero interval enables it.
         */
        internal fun encodeDoubleReminder(intervalMinutes: Int): ByteArray {
            require(intervalMinutes == 0 || intervalMinutes in DOUBLE_REMINDER_INTERVALS) {
                "Unsupported P1 double-reminder interval: $intervalMinutes"
            }
            return encodeCommand(
                0x50,
                if (intervalMinutes > 0) 0x01 else 0x00,
                0x00,
                (intervalMinutes shr 8).toByte(),
                intervalMinutes.toByte(),
                0x01,
            )
        }

        /** Validates the P1 framing, declared payload length, and checksum. */
        internal fun isValidCommandFrame(value: ByteArray): Boolean =
            value.size >= 3 &&
                value[0] == 0x09.toByte() &&
                (value[1].toInt() and 0xff) == value.size - 3 &&
                (value.sumOf { it.toInt() and 0xff } and 0xff) == 0

        /**
         * Returns a checksum-valid P1 payload. Its [P1Payload.command] is the first payload byte;
         * indexed and iterated data starts after that command byte.
         */
        internal fun decodeCommand(value: ByteArray): P1Payload? =
            if (isValidCommandFrame(value) && value.size > 3) P1Payload(value.copyOfRange(2, value.lastIndex)) else null

        internal class P1Payload(private val value: ByteArray) : Iterable<Byte> {
            val command: Byte = value[0]
            val size: Int get() = value.size - 1

            operator fun get(index: Int): Byte = value[index + 1]

            fun uint(index: Int): Int = get(index).toInt() and 0xff

            fun be16(offset: Int): Int = (uint(offset) shl 8) or uint(offset + 1)

            fun be32(offset: Int): Long = (uint(offset).toLong() shl 24) or
                (uint(offset + 1).toLong() shl 16) or
                (uint(offset + 2).toLong() shl 8) or uint(offset + 3).toLong()

            override fun iterator(): Iterator<Byte> = value.copyOfRange(1, value.size).iterator()
        }

        /**
         * `09 03 45 [percentage] [charging status] [checksum]`.
         * Status `00` is charging, `01` is finished charging, and `02` is normal.
         */
        internal fun parseChargingStatus(payload: P1Payload): ChargingStatus? {
            if (payload.size != 2) return null

            val level = payload.uint(0)
            if (level !in 0..100) return null

            val state = when (payload.uint(1)) {
                0x00 -> BatteryState.BATTERY_CHARGING
                0x01 -> BatteryState.BATTERY_CHARGING_FULL
                0x02 -> BatteryState.BATTERY_NORMAL
                else -> return null
            }

            return ChargingStatus(level, state)
        }

        internal data class ChargingStatus(val level: Int, val state: BatteryState)

        /**
         * `09 10 47` is the response to the `0x46` configuration read.
         * It has the same 15-byte layout as `0x30`. 
         * GB reads only the user-exposed fields and validates every value;
         * it deliberately ignores the known mismatching lateral-angle field
         * and all unknown/default-only bytes.
         */
        internal fun parseReminderSettings(payload: P1Payload): ReminderSettings? {
            if (payload.size != 15) return null

            val angleDegrees = payload.uint(2)
            val delaySeconds = payload.uint(11)
            val exerciseAngleDegrees = payload.uint(13)
            val sedentarySeconds = payload.be16(8)
            val sedentaryMinutes = sedentarySeconds / 60
            if (payload.uint(0) !in 0..1 ||
                angleDegrees !in REMINDER_ANGLES ||
                delaySeconds !in REMINDER_DELAYS ||
                exerciseAngleDegrees !in EXERCISE_REMINDER_ANGLES ||
                sedentarySeconds % 60 != 0 || sedentaryMinutes !in SEDENTARY_REMINDER_MINUTES ||
                payload.uint(12) !in 0..1
            ) return null

            return ReminderSettings(
                longVibration = payload.uint(0) == 0,
                angleDegrees = angleDegrees,
                exerciseAngleDegrees = exerciseAngleDegrees,
                sedentaryMinutes = sedentaryMinutes,
                delaySeconds = delaySeconds,
                vibrationDisabled = payload.uint(12) == 1,
            )
        }

        internal data class ReminderSettings(
            val longVibration: Boolean,
            val angleDegrees: Int,
            val exerciseAngleDegrees: Int,
            val sedentaryMinutes: Int,
            val delaySeconds: Int,
            val vibrationDisabled: Boolean,
        )

        /** `09 06 51 [enabled] ...` acknowledges the double-reminder setting. */
        internal fun parseDoubleReminderStatus(payload: P1Payload): Boolean? {
            // The disabled response is one byte shorter than the enabled response; the
            // enabled flag is the only field used by GB.
            if (payload.size !in 4..5) return null
            return when (payload.uint(0)) {
                0 -> false
                1 -> true
                else -> null
            }
        }

        /** `09 04 33` is the delayed response to setting standard posture. */
        internal fun parseStandardPostureResult(payload: P1Payload): StandardPostureResult? {
            if (payload.size != 3) return null
            val code = payload.uint(0)
            val calibrationDecision = payload[1].toInt()
            val rawThirdByte = payload.uint(2)
            return StandardPostureResult(
                code = code,
                calibrationDecision = calibrationDecision,
                rawThirdByte = rawThirdByte,
                success = calibrationDecision < -45,
            )
        }

        internal data class StandardPostureResult(
            /** Unsigned raw AA byte; its semantics are unknown and it is not an angle. */
            val code: Int,
            /** Signed raw BB byte. */
            val calibrationDecision: Int,
            /** Unsigned raw CC byte; its semantics remain unknown. */
            val rawThirdByte: Int,
            val success: Boolean,
        )

        /** 09 11 35 samples carry inclination in hundredths of a degree */
        internal fun parseLivePosture(payload: P1Payload): Double? {
            if (payload.size != 16) return null
            // Byte 4 as the forward angle. Byte 5 is the bank angle
            // and must not be combined with it as a 16-bit value.
            return payload.uint(4).toDouble()
        }

        /**
         * `09 08 04 [battery] [firmware BE16] [free storage BE32]` is the response to `0x03`.
         */
        internal fun parseDeviceStatus(payload: P1Payload): DeviceStatus? {
            if (payload.size != 7) return null

            val batteryLevel = payload.uint(0)
            if (batteryLevel > 100) return null
            val firmware = payload.be16(1)
            val freeStorageRaw = payload.be32(3)
            val freeStoragePercent = minOf(100, (freeStorageRaw * 100 / 1024).toInt())
            return DeviceStatus(batteryLevel, "V0.$firmware", freeStorageRaw, freeStoragePercent)
        }

        /** Retained for existing callers; use [parseDeviceStatus] for the full `0x04` response. */
        internal fun parseFirmwareVersion(payload: P1Payload): String? = parseDeviceStatus(payload)?.firmwareVersion

        internal data class DeviceStatus(
            val batteryLevel: Int,
            val firmwareVersion: String,
            val freeStorageRaw: Long, // Raw unsigned BE32 value
            val freeStoragePercent: Int,
        )

        // 9-byte serial from advertising data after a leading byte.
        // GB's connected support layer receives no scan record: only discovery candidates retain
        // manufacturer-specific scan data. Do not derive a serial from the device name or MAC until
        // the exact AD structure is capture-backed and deliberately persisted into this layer.

        /** The final two bytes vary between the two framed binding captures. */
        internal fun isBindingConfirmation(payload: P1Payload): Boolean {
            return payload.size == 7 &&
                payload[1] == 0x00.toByte() &&
                payload[2] == 0x1d.toByte() &&
                payload[3] == 0x00.toByte() &&
                payload[4] == 0x00.toByte() &&
                payload[5] == 0x03.toByte()
        }
    }
}
