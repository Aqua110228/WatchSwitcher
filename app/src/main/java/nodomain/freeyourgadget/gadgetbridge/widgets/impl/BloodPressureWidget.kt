package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory

/**
 * Latest systolic/diastolic reading, colored per the WHO classification.
 */
object BloodPressureWidget : GaugeWidget<BloodPressureWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(BloodPressureWidget::class.java)

    override val id = "bloodpressure"
    override val label = R.string.blood_pressure
    override val icon = R.drawable.ic_pressure
    override val chartTab = "bloodpressure"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsBloodPressureMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latestSystolic = 0
        var latestDiastolic = 0
        var latestTimestamp = 0L

        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val provider = dev.deviceCoordinator.getBloodPressureSampleProvider(dev, db.daoSession) ?: continue
                    val samples: List<BloodPressureSample> = provider.getAllSamples(
                        scope.query.timeFrom * 1000L,
                        scope.query.timeTo * 1000L
                    )
                    if (samples.isNotEmpty()) {
                        val latest = samples.last()
                        if (latest.timestamp > latestTimestamp) {
                            latestTimestamp = latest.timestamp
                            latestSystolic = latest.bpSystolic
                            latestDiastolic = latest.bpDiastolic
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get blood pressure samples", e)
        }

        return Data(latestSystolic, latestDiastolic)
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        if (data.systolic > 0 && data.diastolic > 0) {
            gaugeValue.text = context.getString(R.string.blood_pressure_avg_format, data.systolic, data.diastolic)

            // WHO classification: color and gauge position based on systolic/diastolic values
            val color: Int
            val gaugeValueFraction: Float
            when {
                data.systolic < 120 && data.diastolic < 80 -> {
                    color = Color.rgb(76, 175, 80) // green - Normal
                    gaugeValueFraction = 0.25f
                }

                data.systolic < 130 && data.diastolic < 80 -> {
                    color = Color.rgb(139, 195, 74) // lime - Elevated
                    gaugeValueFraction = 0.45f
                }

                data.systolic < 140 || data.diastolic < 90 -> {
                    color = Color.rgb(255, 152, 0) // orange - Stage 1
                    gaugeValueFraction = 0.65f
                }

                else -> {
                    color = Color.rgb(244, 67, 54) // red - Stage 2+
                    gaugeValueFraction = 0.88f
                }
            }
            drawSimpleGauge(gaugeBar, color, gaugeValueFraction)
        } else {
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            drawSimpleGauge(gaugeBar, Color.GRAY, -1f)
        }
    }

    data class Data(val systolic: Int, val diastolic: Int)
}
