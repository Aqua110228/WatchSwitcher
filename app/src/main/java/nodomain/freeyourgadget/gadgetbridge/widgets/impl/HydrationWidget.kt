package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object HydrationWidget : GaugeWidget<HydrationWidget.Data>() {
    override val id = "hydration"
    override val label = R.string.pref_header_hydration
    override val icon = R.drawable.ic_drink
    override val chartTab = "hydration"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsHydration(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val devices = scope.devices.filter { it.deviceCoordinator.supportsHydration(it) }
        val date = LocalDate.ofInstant(Instant.ofEpochSecond(scope.query.timeTo.toLong()), ZoneId.systemDefault())
        val day = HydrationSampleProvider.toDay(date)
        val totalMl = scope.db { db ->
            devices.sumOf { device ->
                device.deviceCoordinator.getHydrationSampleProvider(device, db.daoSession)?.getDayTotal(day) ?: 0.0
            }
        }
        val unit = devices.firstOrNull()?.let { HydrationUnit.forDevice(it) } ?: HydrationUnit.MILLILITER
        return Data(totalMl, ActivityUser().hydrationGoalMl, unit)
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        gaugeValue.text = if (data.unit == HydrationUnit.MILLILITER && data.totalMl >= 1000) {
            val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(data.totalMl / 1000) + " " + context.getString(R.string.unit_liter)
        } else {
            data.unit.format(context, data.totalMl)
        }
        val factor = (data.totalMl / data.goalMl).toFloat().coerceIn(0f, 1f)
        drawSimpleGauge(gaugeBar, ContextCompat.getColor(context, R.color.hydration_color), factor)
    }

    data class Data(val totalMl: Double, val goalMl: Int, val unit: HydrationUnit)
}
