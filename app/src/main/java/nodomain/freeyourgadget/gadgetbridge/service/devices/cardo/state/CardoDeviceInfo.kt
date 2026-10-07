package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.FirmwareInfo

class CardoDeviceInfo {
    private val _friendlyName = MutableStateFlow<String?>(null)
    val friendlyName: StateFlow<String?> = _friendlyName.asStateFlow()

    private val _serialNumber = MutableStateFlow<String?>(null)
    val serialNumber: StateFlow<String?> = _serialNumber.asStateFlow()

    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    private val _headsetType = MutableStateFlow<Int?>(null)
    val headsetType: StateFlow<Int?> = _headsetType.asStateFlow()

    fun updateFriendlyName(value: String) {
        _friendlyName.value = value
    }

    fun updateSerialNumber(value: String) {
        _serialNumber.value = value
    }

    fun updateFirmwareInfo(info: FirmwareInfo) {
        _firmwareVersion.value = info.version
        _headsetType.value = info.headsetType
    }

    fun toPreferencesMap(): Map<String, Any?> = mapOf(
        "CardoDeviceInfo_friendlyName" to friendlyName.value,
        "CardoDeviceInfo_serialNumber" to serialNumber.value,
        "CardoDeviceInfo_firmwareVersion" to firmwareVersion.value,
        "CardoDeviceInfo_headsetType" to headsetType.value
    )

    fun restoreFrom(prefs: SharedPreferences) {
        prefs.getString("CardoDeviceInfo_friendlyName", null)?.let { _friendlyName.value = it }
        prefs.getString("CardoDeviceInfo_serialNumber", null)?.let { _serialNumber.value = it }
        prefs.getString("CardoDeviceInfo_firmwareVersion", null)?.let { _firmwareVersion.value = it }
        if (prefs.contains("CardoDeviceInfo_headsetType")) {
            _headsetType.value = prefs.getInt("CardoDeviceInfo_headsetType", 0)
        }
    }
}