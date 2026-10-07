package nodomain.freeyourgadget.gadgetbridge.model.workouts

/**
 * The enum constant with the given [Enum.name], or null when there is none.
 */
inline fun <reified T : Enum<T>> enumByName(name: String?): T? =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
