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
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi_hipee

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.hipee.XiaomiHipeeP1HistoryActivity
import nodomain.freeyourgadget.gadgetbridge.activities.hipee.XiaomiHipeeP1LivePostureActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.deviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.Device
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1ReadingDao
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi_hipee.XiaomiHipeeP1Support
import java.util.regex.Pattern

/** Xiaomi Hipee Smart Posture Corrector P1. FCC ID: 2A2AW-P1 */
class XiaomiHipeeP1Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("^Hi-JZ-12CUT00[0-9A-F]{4}$")

    override fun getManufacturer(): String = "Xiaomi"

    override fun getDeviceNameResource(): Int = R.string.devicetype_xiaomi_hipee_p1

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        XiaomiHipeeP1Support::class.java

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.FITNESS_BAND

    override fun supportsDataFetching(device: GBDevice): Boolean = true

    override fun getCustomActions(): List<DeviceCardAction> = listOf(
        deviceCardAction {
            icon = { R.drawable.ic_angle }
            description = { _, context -> context.getString(R.string.mi_hipee_p1_view_live_posture) }
            isVisible = { device -> device.isInitialized }
            onClick = { device, context ->
                context.startActivity(Intent(context, XiaomiHipeeP1LivePostureActivity::class.java).apply {
                    putExtra(GBDevice.EXTRA_DEVICE, device)
                })
            }
        },
        deviceCardAction {
            icon = { R.drawable.ic_activity_graphs }
            description = { _, context -> context.getString(R.string.controlcenter_start_activitymonitor) }
            isVisible = { _ -> true }
            onClick = { device, context ->
                context.startActivity(Intent(context, XiaomiHipeeP1HistoryActivity::class.java).apply {
                    putExtra(GBDevice.EXTRA_DEVICE, device)
                })
            }
        },
    )

    override fun deleteDevice(@NonNull gbDevice: GBDevice, @NonNull device: Device, @NonNull session: DaoSession) {
        session.xiaomiHipeeP1ReadingDao.queryBuilder()
            .where(XiaomiHipeeP1ReadingDao.Properties.DeviceId.eq(device.id))
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        var standardPostureDialog: AlertDialog? = null
        var standardPostureMessageView: TextView? = null
        var standardPostureProgressBar: ProgressBar? = null

        action(
            key = PREF_XIAOMI_HIPEE_P1_SET_STANDARD_POSTURE,
            title = R.string.mi_hipee_p1_set_standard_posture,
            icon = R.drawable.ic_angle_calibrate,
        ) { context, device ->
            if (standardPostureDialog != null) return@action true

            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val messageView = TextView(context).apply {
                text = context.getString(R.string.mi_hipee_p1_set_standard_posture_instruction)
                setPadding(padding, 0, 0, 0)
            }
            standardPostureMessageView = messageView
            val progressBar = ProgressBar(context).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            standardPostureProgressBar = progressBar

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(padding, padding, padding, padding)
                addView(progressBar)
                addView(messageView)
            }

            standardPostureDialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.mi_hipee_p1_set_standard_posture)
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    standardPostureDialog = null
                    standardPostureMessageView = null
                    standardPostureProgressBar = null
                    dialog.dismiss()
                    GBApplication.deviceService(device).onSendConfiguration(PREF_XIAOMI_HIPEE_P1_CANCEL_STANDARD_POSTURE)
                }
                .show()
            GBApplication.deviceService(device).onSendConfiguration(PREF_XIAOMI_HIPEE_P1_SET_STANDARD_POSTURE)
            true
        }
        text(
            key = PREF_XIAOMI_HIPEE_P1_STANDARD_POSTURE_STATUS,
            title = R.string.mi_hipee_p1_set_standard_posture,
            connectedOnly = false,
            visibleWhen = { false },
            onSharedPreferenceChanged = { value ->
                val dialog = standardPostureDialog ?: return@text
                when {
                    value == STANDARD_POSTURE_STATUS_RUNNING -> {
                        standardPostureProgressBar?.visibility = View.GONE
                        standardPostureMessageView?.text = dialog.context.getString(
                            R.string.mi_hipee_p1_set_standard_posture_instruction,
                        )
                    }
                    value == STANDARD_POSTURE_STATUS_SUCCESS -> {
                        standardPostureProgressBar?.visibility = View.GONE
                        standardPostureMessageView?.text = dialog.context.getString(R.string.mi_hipee_p1_standard_posture_set)
                        Handler(Looper.getMainLooper()).postDelayed({
                            standardPostureDialog?.dismiss()
                            standardPostureDialog = null
                            standardPostureMessageView = null
                            standardPostureProgressBar = null
                        }, STANDARD_POSTURE_SUCCESS_CLOSE_DELAY_MS)
                    }
                    value == STANDARD_POSTURE_STATUS_CANCELLED -> {
                        dialog.dismiss()
                        standardPostureDialog = null
                        standardPostureMessageView = null
                        standardPostureProgressBar = null
                    }
                    value == STANDARD_POSTURE_STATUS_IN_PROGRESS -> {
                        standardPostureProgressBar?.visibility = View.VISIBLE
                    }
                    value.startsWith(STANDARD_POSTURE_STATUS_RETRY_PREFIX) -> {
                        standardPostureProgressBar?.visibility = View.VISIBLE
                        val code = value.removePrefix(STANDARD_POSTURE_STATUS_RETRY_PREFIX).toIntOrNull() ?: 0
                        standardPostureMessageView?.text = dialog.context.getString(
                            R.string.mi_hipee_p1_standard_posture_rejected,
                            code,
                        )
                    }
                    else -> standardPostureMessageView?.text = dialog.context.getString(
                        R.string.mi_hipee_p1_set_standard_posture_instruction,
                    )
                }
            },
        )
        switchSetting(
            key = PREF_VIBRATION_ENABLE,
            title = R.string.title_activity_vibration,
            icon = R.drawable.ic_vibration,
            defaultValue = true,
        )
        switchSetting(
            key = PREF_XIAOMI_HIPEE_P1_LONG_VIBRATION,
            title = R.string.mi_hipee_p1_long_vibration,
            icon = R.drawable.ic_vibration,
            defaultValue = false,
        )
        list(
            key = PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE,
            title = R.string.mi_hipee_p1_reminder_angle,
            icon = R.drawable.ic_angle,
            entries = listOf(
                ListEntry.Text("5", "5°"),
                ListEntry.Text("10", "10°"),
                ListEntry.Text("15", "15°"),
                ListEntry.Text("20", "20°"),
                ListEntry.Text("25", "25°"),
                ListEntry.Text("30", "30°"),
            ),
            defaultValue = "10",
        )
        list(
            key = PREF_XIAOMI_HIPEE_P1_DELAY_REMINDER,
            title = R.string.mi_hipee_p1_delay_reminder,
            icon = R.drawable.ic_timer,
            entries = listOf(
                ListEntry.Res("0", R.string.no_delay),
                ListEntry.Text("2", "2 s"),
                ListEntry.Text("3", "3 s"),
                ListEntry.Text("5", "5 s"),
                ListEntry.Text("10", "10 s"),
                ListEntry.Text("20", "20 s"),
            ),
            defaultValue = "3",
        )
        list(
            key = PREF_XIAOMI_HIPEE_P1_EXERCISE_REMINDER_ANGLE,
            title = R.string.mi_hipee_p1_exercise_reminder_angle,
            icon = R.drawable.ic_angle,
            entries = listOf(
                ListEntry.Text("5", "5°"),
                ListEntry.Text("10", "10°"),
                ListEntry.Text("15", "15°"),
                ListEntry.Text("20", "20°"),
            ),
            defaultValue = "10",
        )
        list(
            key = PREF_XIAOMI_HIPEE_P1_SEDENTARY_REMINDER,
            title = R.string.mi_hipee_p1_sedentary_reminder,
            icon = R.drawable.ic_timer,
            entries = listOf(
                ListEntry.Res("0", R.string.none),
                ListEntry.Text("60", "1 h"),
                ListEntry.Text("120", "2 h"),
                ListEntry.Text("180", "3 h"),
                ListEntry.Text("240", "4 h"),
            ),
            defaultValue = "60",
        )
        if (getFirmwareVersion(device) > 26) {
            list(
                key = PREF_XIAOMI_HIPEE_P1_DOUBLE_REMINDER_INTERVAL,
                title = R.string.mi_hipee_p1_double_reminder,
                icon = R.drawable.ic_timer,
                entries = listOf(
                    ListEntry.Res("0", R.string.none),
                    ListEntry.Text("15", "15 min"),
                    ListEntry.Text("30", "30 min"),
                    ListEntry.Text("60", "1 h"),
                    ListEntry.Text("120", "2 h"),
                    ListEntry.Text("180", "3 h"),
                ),
                defaultValue = "30",
            )
        }
    }

    private fun getFirmwareVersion(device: GBDevice): Int =
        device.firmwareVersion?.removePrefix("V0.")?.toIntOrNull() ?: 0

    private companion object {
        const val STANDARD_POSTURE_SUCCESS_CLOSE_DELAY_MS = 3_000L
        const val STANDARD_POSTURE_STATUS_RUNNING = "RUNNING"
        const val STANDARD_POSTURE_STATUS_SUCCESS = "SUCCESS"
        const val STANDARD_POSTURE_STATUS_CANCELLED = "CANCELLED"
        const val STANDARD_POSTURE_STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STANDARD_POSTURE_STATUS_RETRY_PREFIX = "RETRY:"
    }
}
