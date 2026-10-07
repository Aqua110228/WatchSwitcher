package nodomain.freeyourgadget.gadgetbridge.model

import android.content.Context
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.NumberFormat
import java.util.Locale

enum class HydrationUnit(
    override val label: Int,
    @StringRes val symbol: Int,
    val ml: Double,
    val decimals: Int,
) : LabeledEntry {
    MILLILITER(R.string.hydration_unit_milliliter, R.string.hydration_unit_milliliter_symbol, 1.0, 0),
    CUP(R.string.hydration_unit_cup, R.string.hydration_unit_cup_symbol, 236.588, 1),
    OUNCE(R.string.hydration_unit_ounce, R.string.hydration_unit_ounce_symbol, 29.5735, 1),
    ;

    val key: String
        get() = name.lowercase(Locale.ROOT)

    /**
     * Converts a volume in mL to this unit.
     */
    fun fromMl(volumeMl: Double): Double = volumeMl / ml

    /**
     * Converts a volume in this unit to the other unit.
     */
    fun convert(volume: Double, to: HydrationUnit): Double = volume * ml / to.ml

    /**
     * Formats a volume in mL as a value in this unit, followed by the unit symbol.
     */
    fun format(context: Context, volumeMl: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        numberFormat.maximumFractionDigits = decimals
        return numberFormat.format(fromMl(volumeMl)) + " " + context.getString(symbol)
    }

    companion object {
        @JvmStatic
        fun fromKey(key: String?): HydrationUnit? = entries.firstOrNull { it.key == key }

        /**
         * The unit in which the device displays hydration volumes.
         */
        @JvmStatic
        fun forDevice(device: GBDevice): HydrationUnit {
            val key = GBApplication.getDevicePrefs(device).getString(
                DeviceSettingsPreferenceConst.PREF_HYDRATION_UNIT,
                null
            )
            return fromKey(key) ?: MILLILITER
        }
    }
}
