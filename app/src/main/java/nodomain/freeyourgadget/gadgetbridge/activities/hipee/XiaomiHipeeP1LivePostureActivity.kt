/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.hipee

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class XiaomiHipeeP1LivePostureActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var postureView: LivePostureView

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updatedDevice = intent.getDevice() ?: return
            if (updatedDevice.address != device.address) return
            val posture = updatedDevice.getDeviceInfo("POSTURE: ")?.details
                ?.removeSuffix("°")
                ?.toFloatOrNull()
            if (posture != null) postureView.setAngle(posture)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        device = requireNotNull(intent.getDevice())
        val reminderAngle = GBApplication.getDevicePrefs(device)
            .getString(PREF_XIAOMI_HIPEE_P1_REMINDER_ANGLE, "10")
            ?.toFloatOrNull()
            ?: 10f
        postureView = LivePostureView(this, reminderAngle)
        setContentView(postureView)
        supportActionBar?.apply {
            setTitle(R.string.mi_hipee_p1_view_live_posture)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            deviceReceiver,
            IntentFilter(GBDevice.ACTION_DEVICE_CHANGED),
        )
        setLivePostureEnabled(true)
    }

    override fun onStop() {
        setLivePostureEnabled(false)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deviceReceiver)
        super.onStop()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setLivePostureEnabled(enabled: Boolean) {
        GBApplication.getDevicePrefs(device).getPreferences().edit()
            .putBoolean(PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE, enabled)
            .apply()
        if (device.isInitialized) {
            GBApplication.deviceService(device).onSendConfiguration(PREF_XIAOMI_HIPEE_P1_VIEW_LIVE_POSTURE)
        }
    }

    private class LivePostureView(
        context: Context,
        private val reminderAngle: Float,
    ) : View(context) {
        private val backgroundColor = GBApplication.getBackgroundColor(context)
        private val normalArcColor = 0xff4f6bed.toInt()
        private val warningArcColor = 0xffd32f2f.toInt()
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = normalArcColor
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xffe05d44.toInt()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 52f
            color = GBApplication.getTextColor(context)
        }
        private var angleDegrees = 0f

        fun setAngle(angle: Float) {
            angleDegrees = angle
            arcPaint.color = if (angle >= reminderAngle) warningArcColor else normalArcColor
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(backgroundColor)

            val radius = min(width * 0.4f, height * 0.36f)
            val centerX = width / 2f
            val centerY = height * 0.68f
            val bounds = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            canvas.drawArc(bounds, 180f, 180f, false, arcPaint)

            val clampedAngle = angleDegrees.coerceIn(-90f, 90f)
            val radians = Math.toRadians(clampedAngle.toDouble())
            val unitX = sin(radians).toFloat()
            val unitY = -cos(radians).toFloat()
            val tipX = centerX + unitX * radius
            val tipY = centerY + unitY * radius

            val headLength = radius * 0.12f
            val headWidth = radius * 0.07f
            val outerTipX = tipX + unitX * headLength
            val outerTipY = tipY + unitY * headLength
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(
                    outerTipX - unitY * headWidth,
                    outerTipY + unitX * headWidth,
                )
                lineTo(
                    outerTipX + unitY * headWidth,
                    outerTipY - unitX * headWidth,
                )
                close()
            }
            canvas.drawPath(path, arrowPaint)
            canvas.drawText(
                String.format(Locale.getDefault(), "%.0f°", angleDegrees),
                centerX,
                centerY + radius * 0.55f,
                textPaint,
            )
        }
    }
}
