package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Chart colors, resolved once from a themed [Context].
 */
data class ChartTheme(
    val textColor: Int,
    val secondaryTextColor: Int,
    val backgroundColor: Int,
    val heartRateColor: Int,
    val heartRateFillColor: Int,
    val activityColor: Int,
    val deepSleepColor: Int,
    val lightSleepColor: Int,
    val remSleepColor: Int,
    val awakeSleepColor: Int,
    val notWornColor: Int,
) {
    companion object {
        private const val PREF_HEARTRATE_ALTERNATIVE_COLOR = "chart_heartrate_color"

        fun from(context: Context): ChartTheme {
            val theme = context.theme
            fun attrColor(attrRes: Int): Int {
                val value = TypedValue()
                theme.resolveAttribute(attrRes, value, true)
                return value.data
            }

            val useAlternativeHeartRateColor = GBApplication.getPrefs()
                .getBoolean(PREF_HEARTRATE_ALTERNATIVE_COLOR, false)

            return ChartTheme(
                textColor = GBApplication.getTextColor(context),
                secondaryTextColor = GBApplication.getSecondaryTextColor(context),
                backgroundColor = GBApplication.getBackgroundColor(context),
                heartRateColor = ContextCompat.getColor(
                    context,
                    if (useAlternativeHeartRateColor) R.color.chart_heartrate_alternative else R.color.chart_heartrate,
                ),
                heartRateFillColor = ContextCompat.getColor(context, R.color.chart_heartrate_fill),
                activityColor = attrColor(R.attr.chart_activity),
                deepSleepColor = attrColor(R.attr.chart_deep_sleep),
                lightSleepColor = attrColor(R.attr.chart_light_sleep),
                remSleepColor = attrColor(R.attr.chart_rem_sleep),
                awakeSleepColor = attrColor(R.attr.chart_awake_sleep),
                notWornColor = attrColor(R.attr.chart_not_worn),
            )
        }
    }
}
