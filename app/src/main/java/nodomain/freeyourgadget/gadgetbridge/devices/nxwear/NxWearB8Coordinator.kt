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
package nodomain.freeyourgadget.gadgetbridge.devices.nxwear

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2SampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider
import nodomain.freeyourgadget.gadgetbridge.model.StressSample
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear.NxWearB8Support
import nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear.NxWearSportSession
import java.util.regex.Pattern

class NxWearB8Coordinator : AbstractBLEDeviceCoordinator() {
    fun getMtu(): Int = MTU

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^B8-SW$")

    override fun getManufacturer(): String = "Nx Wear"

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> = NxWearB8Support::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_nx_wear_b8

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec {
        val preferences = GBApplication.getDevicePrefs(device).preferences
        val key = DeviceSettingsPreferenceConst.PREF_ACTIVATE_DISPLAY_ON_LIFT
        val stored = preferences.all[key]
        if (stored is Boolean || !preferences.contains(PREF_LIFT_ENABLED)) {
            val enabled = when (stored) {
                is Boolean -> stored
                is String -> stored != "off"
                else -> false
            }
            preferences.edit()
                .putString(key, if (enabled) "scheduled" else "off")
                .putBoolean(PREF_LIFT_ENABLED, enabled)
                .apply()
        }
        val measurementDialog = NxWearB8MeasurementDialog(preferences)
        return deviceSettings {
            screen(
                key = "nx_wear_b8_health",
                title = R.string.pref_header_health,
                icon = R.drawable.ic_heartrate,
            ) {
                switchSetting(
                    key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_AUTOMATIC_ENABLE,
                    title = R.string.pref_heartrate_automatic_enable,
                    icon = R.drawable.ic_heartrate,
                    defaultValue = false,
                )
                switchSetting(
                    key = DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING,
                    title = R.string.prefs_spo2_monitoring_title,
                    icon = R.drawable.ic_spo2,
                    defaultValue = false,
                )
                list(
                    key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL,
                    title = R.string.pref_title_time_interval,
                    icon = R.drawable.ic_timer,
                    entries = getHeartRateMeasurementIntervals().map {
                        ListEntry.Res(it.getIntervalSeconds().toString(), it.getLabel())
                    },
                    defaultValue = HeartRateCapability.MeasurementInterval.MINUTES_30.getIntervalSeconds().toString(),
                )
                action(
                    key = ACTION_MEASURE_STRESS,
                    title = R.string.nx_wear_b8_measure_stress,
                    icon = R.drawable.ic_stress,
                ) { context, device ->
                    if (device != null) {
                        measurementDialog.show(context, R.string.nx_wear_b8_measure_stress) {
                            GBApplication.deviceService(device).onSendConfiguration(ACTION_CANCEL_MEASURE_STRESS)
                        }
                        GBApplication.deviceService(device).onSendConfiguration(ACTION_MEASURE_STRESS)
                    }
                    device != null
                }
                action(
                    key = ACTION_MEASURE_SPO2,
                    title = R.string.nx_wear_b8_measure_spo2,
                    icon = R.drawable.ic_spo2,
                ) { context, device ->
                    if (device != null) {
                        measurementDialog.show(context, R.string.nx_wear_b8_measure_spo2) {
                            GBApplication.deviceService(device).onSendConfiguration(ACTION_CANCEL_MEASURE_SPO2)
                        }
                        GBApplication.deviceService(device).onSendConfiguration(ACTION_MEASURE_SPO2)
                    }
                    device != null
                }
            }
            screen(
                key = "nx_wear_b8_exercise",
                title = R.string.activity_type_exercise,
                icon = R.drawable.ic_activity_free_training,
            ) {
                list(
                    key = PREF_EXERCISE_SPORT,
                    title = R.string.mi5_prefs_workout_activity_types,
                    icon = R.drawable.ic_activity_free_training,
                    entries = NxWearSportSession.SPORTS.map {
                        ListEntry.Res(it.type.toString(), it.activityKind.getLabel())
                    },
                    defaultValue = NxWearSportSession.OUTDOOR_RUNNING.toString(),
                )
                action(
                    key = ACTION_EXERCISE_START,
                    title = R.string.live_activity_start_your_activity,
                    icon = R.drawable.ic_play,
                    visibleWhen = { prefs -> !prefs.getBoolean(PREF_EXERCISE_RUNNING, false) },
                ) { context, device ->
                    if (device != null) {
                        GBApplication.deviceService(device).onSendConfiguration(ACTION_EXERCISE_START)
                    }
                    device != null
                }
                action(
                    key = ACTION_EXERCISE_STOP,
                    title = R.string.stop,
                    icon = R.drawable.ic_stop,
                    visibleWhen = { prefs -> prefs.getBoolean(PREF_EXERCISE_RUNNING, false) },
                ) { context, device ->
                    if (device != null) {
                        GBApplication.deviceService(device).onSendConfiguration(ACTION_EXERCISE_STOP)
                    }
                    device != null
                }
            }
            screen(
                key = "nx_wear_b8_display",
                title = R.string.mi2_prefs_activate_display_on_lift,
                icon = R.drawable.ic_arrow_upward,
            ) {
                switchSetting(
                    key = PREF_LIFT_ENABLED,
                    title = R.string.mi2_prefs_activate_display_on_lift,
                    icon = R.drawable.ic_arrow_upward,
                    defaultValue = false,
                )
                list(
                    key = DeviceSettingsPreferenceConst.PREF_ACTIVATE_DISPLAY_ON_LIFT,
                    title = R.string.mi2_prefs_activate_display_on_lift,
                    entries = listOf(ListEntry.Res("off", R.string.off), ListEntry.Res("scheduled", R.string.on)),
                    defaultValue = "off",
                    visibleWhen = { false },
                )
                time(
                    key = DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_START,
                    title = R.string.mi2_prefs_do_not_disturb_start,
                    icon = R.drawable.ic_wb_sunny,
                    defaultValue = "09:00",
                    dependency = PREF_LIFT_ENABLED,
                )
                time(
                    key = DeviceSettingsPreferenceConst.PREF_DISPLAY_ON_LIFT_END,
                    title = R.string.mi2_prefs_do_not_disturb_end,
                    icon = R.drawable.ic_nights_stay,
                    defaultValue = "23:59",
                    dependency = PREF_LIFT_ENABLED,
                )
                list(
                    key = DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT,
                    title = R.string.prefs_screen_timeout,
                    icon = R.drawable.ic_timer,
                    entries = (3..10).map { ListEntry.Text(it.toString(), "$it s") },
                    defaultValue = "5",
                    dependency = PREF_LIFT_ENABLED,
                )
            }
            switchSetting(
                key = PREF_SHAKE_TO_TAKE_PICTURE,
                title = R.string.nx_wear_b8_shake_to_take_picture,
                icon = R.drawable.ic_camera_remote,
                defaultValue = false,
            )
            list(
                key = DeviceSettingsPreferenceConst.PREF_TIMEFORMAT,
                title = R.string.pref_title_timeformat,
                icon = R.drawable.ic_timer,
                entriesRes = R.array.pref_timeformat_entries,
                entryValuesRes = R.array.pref_timeformat_values,
                defaultValue = DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_AUTO,
            )
        }
    }

    override fun getHeartRateMeasurementIntervals(): List<HeartRateCapability.MeasurementInterval> = listOf(
        HeartRateCapability.MeasurementInterval.MINUTES_30,
        HeartRateCapability.MeasurementInterval.HOUR_1,
    )

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind = DeviceCoordinator.DeviceKind.WATCH

    override fun supportsPowerOff(device: GBDevice): Boolean = true

    override fun getMaxHeartRateMeasurementsGapMinutes(device: GBDevice): Int = 60

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean = true

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean = true

    override fun supportsRealtimeData(device: GBDevice): Boolean = true

    override fun supportsDataFetching(device: GBDevice): Boolean = true

    override fun supportsSleepMeasurement(device: GBDevice): Boolean = true

    override fun getActivityTrackProvider(device: GBDevice, context: Context): ActivityTrackProvider = NxWearActivityTrackProvider(device)

    override fun supportsActivityTracking(device: GBDevice): Boolean = true

    override fun supportsActivityDistance(device: GBDevice): Boolean = true

    override fun supportsActiveCalories(device: GBDevice): Boolean = true

    override fun supportsStressMeasurement(device: GBDevice): Boolean = true

    override fun supportsSpo2(device: GBDevice): Boolean = true

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out AbstractActivitySample> =
        NxWearActivitySampleProvider(device, session)

    override fun getStressSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out StressSample> =
        GenericStressSampleProvider(device, session)

    override fun getSpo2SampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out Spo2Sample> =
        GenericSpo2SampleProvider(device, session)

    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>, Property> =
        mutableMapOf<AbstractDao<*, *>, Property>(
            session.genericSleepStageSampleDao to nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSampleDao.Properties.DeviceId,
            session.genericHeartRateSampleDao to GenericHeartRateSampleDao.Properties.DeviceId,
            session.genericSpo2SampleDao to GenericSpo2SampleDao.Properties.DeviceId,
            session.genericStressSampleDao to GenericStressSampleDao.Properties.DeviceId,
            session.nxWearSportSampleDao to nodomain.freeyourgadget.gadgetbridge.entities.NxWearSportSampleDao.Properties.DeviceId,
        )

    companion object {
        const val PREF_LIFT_ENABLED = "nx_wear_b8_lift_enabled"
        const val PREF_SHAKE_TO_TAKE_PICTURE = "nx_wear_b8_shake_to_take_picture"
        const val PREF_MEASUREMENT_RESULT = "nx_wear_b8_measurement_result"
        const val ACTION_MEASURE_STRESS = "nx_wear_b8_measure_stress"
        const val ACTION_MEASURE_SPO2 = "nx_wear_b8_measure_spo2"
        const val ACTION_CANCEL_MEASURE_STRESS = "nx_wear_b8_cancel_measure_stress"
        const val ACTION_CANCEL_MEASURE_SPO2 = "nx_wear_b8_cancel_measure_spo2"
        const val PREF_EXERCISE_SPORT = "nx_wear_b8_exercise_sport"
        const val ACTION_EXERCISE_START = "nx_wear_b8_exercise_start"
        const val ACTION_EXERCISE_STOP = "nx_wear_b8_exercise_stop"
        const val PREF_EXERCISE_RUNNING = "nx_wear_b8_exercise_running"
        private const val MTU = 517
    }
}

private class NxWearB8MeasurementDialog(
    private val preferences: SharedPreferences,
) {
    private var dialog: AlertDialog? = null
    private val MEASUREMENT_RESULT_CLOSE_DELAY_MS = 5_000L

    fun show(context: android.content.Context, title: Int, onCancel: () -> Unit) {
        dialog?.dismiss()
        // Results are transient: an EditTextPreference would restore its cached value when cleared.
        // Observe only the active dialog, independently of settings-spec recreation.
        preferences.edit().remove(NxWearB8Coordinator.PREF_MEASUREMENT_RESULT).apply()

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val messageView = TextView(context).apply {
            text = context.getString(R.string.nx_wear_b8_measurement_in_progress)
            setPadding(padding, 0, 0, 0)
        }
        val progressView = ProgressBar(context).apply { isIndeterminate = true }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
            addView(progressView)
            addView(messageView)
        }
        val currentDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .create()
        val handler = Handler(Looper.getMainLooper())
        val closeRunnable = Runnable { currentDialog.dismiss() }
        var resultReceived = false
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == NxWearB8Coordinator.PREF_MEASUREMENT_RESULT) {
                val value = prefs.getString(key, "").orEmpty()
                if (value.isNotEmpty()) {
                    handler.post {
                        if (currentDialog.isShowing && !resultReceived) {
                            resultReceived = true
                            progressView.visibility = View.GONE
                            messageView.text = value
                            currentDialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility = View.GONE
                            handler.postDelayed(closeRunnable, MEASUREMENT_RESULT_CLOSE_DELAY_MS)
                        }
                    }
                }
            }
        }
        currentDialog.setOnDismissListener {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            handler.removeCallbacksAndMessages(null)
            if (dialog === currentDialog) dialog = null
        }
        dialog = currentDialog
        currentDialog.show()
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
