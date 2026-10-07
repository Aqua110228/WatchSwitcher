package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats a value that is an epoch-second timestamp, using [SimpleDateFormat] [pattern].
 */
internal fun timeOfDayValueFormatter(pattern: String): CartesianValueFormatter =
    object : CartesianValueFormatter {
        private val format = SimpleDateFormat(pattern, Locale.getDefault())

        override fun format(
            context: CartesianMeasuringContext,
            value: Double,
            verticalAxisPosition: Axis.Position.Vertical?,
        ): CharSequence = format.format(value.toLong() * 1000L)
    }

/**
 * Formats a value that is a duration in seconds, as `H:MM:SS` (or `MM:SS` under an hour).
 */
internal fun durationValueFormatter(): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        val totalSeconds = value.toLong()
        val hours = TimeUnit.SECONDS.toHours(totalSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60
        if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
