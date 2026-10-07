package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * Pool swim equipment.
 */
enum class WorkoutEquipment(override val label: Int) : LabeledEntry {
    NONE(R.string.none),
    FINS(R.string.workout_equipment_fins),
    KICKBOARD(R.string.workout_equipment_kickboard),
    PADDLES(R.string.workout_equipment_paddles),
    PULL_BUOY(R.string.workout_equipment_pull_buoy),
    SNORKEL(R.string.workout_equipment_snorkel),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutEquipment? = enumByName(name)
    }
}
