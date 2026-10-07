package nodomain.freeyourgadget.gadgetbridge.model

import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import java.util.Locale

object HydrationContainer {
    const val COUNT = 3

    private val DEFAULT_VOLUMES = intArrayOf(250, 500, 750)

    @JvmStatic
    fun volumeKey(container: Int): String = String.format(Locale.ROOT, "hydration_container_%d_volume", container)

    @JvmStatic
    fun unitKey(container: Int): String = String.format(Locale.ROOT, "hydration_container_%d_unit", container)

    /**
     * The key of the container volume in mL before the last unit conversion.
     */
    @JvmStatic
    fun volumeMlKey(container: Int): String = String.format(Locale.ROOT, "hydration_container_%d_volume_ml", container)

    /**
     * The default volume of the container, in milliliters.
     */
    @JvmStatic
    fun defaultVolume(container: Int): Int = DEFAULT_VOLUMES[container - 1]

    /**
     * The volume of the container, in the unit of the container.
     */
    @JvmStatic
    fun getVolume(prefs: Prefs, container: Int): Double {
        return prefs.getString(volumeKey(container), null)?.toDoubleOrNull()
            ?: defaultVolume(container).toDouble()
    }

    @JvmStatic
    fun getUnit(prefs: Prefs, container: Int): HydrationUnit {
        return HydrationUnit.fromKey(prefs.getString(unitKey(container), null)) ?: HydrationUnit.MILLILITER
    }

    @JvmStatic
    fun getVolumeMl(prefs: Prefs, container: Int): Double {
        return getVolume(prefs, container) * getUnit(prefs, container).ml
    }
}
