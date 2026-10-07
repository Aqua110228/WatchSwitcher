package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import nodomain.freeyourgadget.gadgetbridge.model.DistanceUnit

/**
 * Per-step display unit override. Does not affect database storage unit.
 */
enum class WorkoutMeasurementSystem(override val label: Int) : LabeledEntry {
    METRIC(R.string.unit_metric),
    IMPERIAL(R.string.unit_imperial),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutMeasurementSystem? = enumByName(name)

        @JvmStatic
        fun globalDefault(): WorkoutMeasurementSystem = when (GBApplication.getPrefs().distanceUnit) {
            DistanceUnit.IMPERIAL -> IMPERIAL
            else -> METRIC
        }
    }
}
