package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import android.content.SharedPreferences
import java.util.Locale

class CardoDeviceStatus {
    val config = CardoConfigState()
    val battery = CardoBatteryState()
    val call = CardoCallStatus()
    val deviceInfo = CardoDeviceInfo()
    val subscribe = CardoSubscribeState()
    val capabilities = CardoDeviceCapabilities()

    fun toPreferencesMap(): Map<String, Any?> = config.toPreferencesMap() + deviceInfo.toPreferencesMap()

    fun restoreFrom(prefs: SharedPreferences) {
        config.restoreFrom(prefs)
        deviceInfo.restoreFrom(prefs)
    }
}

fun <T : Enum<T>> T.toPrefString(): String = name.lowercase(Locale.ROOT)

inline fun <reified T : Enum<T>> SharedPreferences.getEnumOrDefault(key: String, default: T): T =
    getString(key, null)?.let { enumValueOf<T>(it.uppercase(Locale.ROOT)) } ?: default