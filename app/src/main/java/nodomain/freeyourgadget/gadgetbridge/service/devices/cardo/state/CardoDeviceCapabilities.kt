package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEqualizerProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoLanguage
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.FmStationList
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeFmStationList
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.DecodedField

class CardoDeviceCapabilities {
    private val _txPowerProfile = MutableStateFlow(0)
    val txPowerProfile: StateFlow<Int> = _txPowerProfile.asStateFlow()
    private val _lrSpeakers = MutableStateFlow(false)
    val lrSpeakers: StateFlow<Boolean> = _lrSpeakers.asStateFlow()
    private val _sensitivity = MutableStateFlow(0)
    val sensitivity: StateFlow<Int> = _sensitivity.asStateFlow()
    private val _isAutomaticVolumeAvailable = MutableStateFlow(false)
    val isAutomaticVolumeAvailable: StateFlow<Boolean> = _isAutomaticVolumeAvailable.asStateFlow()
    private val _isMusicSharingAvailable = MutableStateFlow(false)
    val isMusicSharingAvailable: StateFlow<Boolean> = _isMusicSharingAvailable.asStateFlow()
    private val _isBluetoothICAvailable = MutableStateFlow(false)
    val isBluetoothICAvailable: StateFlow<Boolean> = _isBluetoothICAvailable.asStateFlow()
    private val _isHFPMixingAvailable = MutableStateFlow(false)
    val isHFPMixingAvailable: StateFlow<Boolean> = _isHFPMixingAvailable.asStateFlow()
    private val _isASRAvailable = MutableStateFlow(false)
    val isASRAvailable: StateFlow<Boolean> = _isASRAvailable.asStateFlow()
    private val _isDMCAvailable = MutableStateFlow(false)
    val isDMCAvailable: StateFlow<Boolean> = _isDMCAvailable.asStateFlow()
    private val _numberOfSupportedICChannels = MutableStateFlow(0)
    val numberOfSupportedICChannels: StateFlow<Int> = _numberOfSupportedICChannels.asStateFlow()
    private val _isDynamicICAvailable = MutableStateFlow(false)
    val isDynamicICAvailable: StateFlow<Boolean> = _isDynamicICAvailable.asStateFlow()
    private val _isOTAAvailable = MutableStateFlow(false)
    val isOTAAvailable: StateFlow<Boolean> = _isOTAAvailable.asStateFlow()
    private val _isFMAvailable = MutableStateFlow(false)
    val isFMAvailable: StateFlow<Boolean> = _isFMAvailable.asStateFlow()
    private val _isPrivateChatAvailable = MutableStateFlow(false)
    val isPrivateChatAvailable: StateFlow<Boolean> = _isPrivateChatAvailable.asStateFlow()
    private val _isEcoModeAvailable = MutableStateFlow(false)
    val isEcoModeAvailable: StateFlow<Boolean> = _isEcoModeAvailable.asStateFlow()
    private val _isMobileBridgeAvailable = MutableStateFlow(false)
    val isMobileBridgeAvailable: StateFlow<Boolean> = _isMobileBridgeAvailable.asStateFlow()
    private val _isLRSpeakersAvailable = MutableStateFlow(false)
    val isLRSpeakersAvailable: StateFlow<Boolean> = _isLRSpeakersAvailable.asStateFlow()
    private val _isICDMCBridgeAvailable = MutableStateFlow(false)
    val isICDMCBridgeAvailable: StateFlow<Boolean> = _isICDMCBridgeAvailable.asStateFlow()
    private val _isAutoOnOffAvailable = MutableStateFlow(false)
    val isAutoOnOffAvailable: StateFlow<Boolean> = _isAutoOnOffAvailable.asStateFlow()
    private val _isCSLNXTVADLicence = MutableStateFlow(false)
    val isCSLNXTVADLicence: StateFlow<Boolean> = _isCSLNXTVADLicence.asStateFlow()
    private val _isAdvancedMMIAvailable = MutableStateFlow(false)
    val isAdvancedMMIAvailable: StateFlow<Boolean> = _isAdvancedMMIAvailable.asStateFlow()
    private val _deviceLetter = MutableStateFlow(0)
    val deviceLetter: StateFlow<Int> = _deviceLetter.asStateFlow()
    private val _softwareVersion = MutableStateFlow(0)
    val softwareVersion: StateFlow<Int> = _softwareVersion.asStateFlow()
    private val _subVersion = MutableStateFlow(0)
    val subVersion: StateFlow<Int> = _subVersion.asStateFlow()
    private val _softwareRevision = MutableStateFlow(0)
    val softwareRevision: StateFlow<Int> = _softwareRevision.asStateFlow()
    private val _isDMCAGCEnabled = MutableStateFlow(false)
    val isDMCAGCEnabled: StateFlow<Boolean> = _isDMCAGCEnabled.asStateFlow()
    private val _isAdvancedMMIEnabled = MutableStateFlow(false)
    val isAdvancedMMIEnabled: StateFlow<Boolean> = _isAdvancedMMIEnabled.asStateFlow()
    private val _isRedialASREnabled = MutableStateFlow(false)
    val isRedialASREnabled: StateFlow<Boolean> = _isRedialASREnabled.asStateFlow()
    private val _isRadioONASREnabled = MutableStateFlow(false)
    val isRadioONASREnabled: StateFlow<Boolean> = _isRadioONASREnabled.asStateFlow()
    private val _isAutoOnOffEnabled = MutableStateFlow(false)
    val isAutoOnOffEnabled: StateFlow<Boolean> = _isAutoOnOffEnabled.asStateFlow()
    private val _isAccessoriesActivated = MutableStateFlow(false)
    val isAccessoriesActivated: StateFlow<Boolean> = _isAccessoriesActivated.asStateFlow()
    private val _languageList = MutableStateFlow<Set<CardoLanguage>>(emptySet())
    val languageList: StateFlow<Set<CardoLanguage>> = _languageList.asStateFlow()
    private val _equalizerProfiles = MutableStateFlow<Set<CardoEqualizerProfile>>(emptySet())
    val equalizerProfiles: StateFlow<Set<CardoEqualizerProfile>> = _equalizerProfiles.asStateFlow()
    private val _fmStationList = MutableStateFlow<FmStationList?>(null)
    val fmStationList: StateFlow<FmStationList?> = _fmStationList.asStateFlow()

    fun update(decodedFields: List<DecodedField>) {
        for (field in decodedFields) {
            when (field.name) {
                "txPowerProfile" -> _txPowerProfile.value = field.value as Int
                "lrSpeakers" -> _lrSpeakers.value = field.value as Boolean
                "sensitivity" -> _sensitivity.value = field.value as Int
                "isAutomaticVolumeAvailable" -> _isAutomaticVolumeAvailable.value = field.value as Boolean
                "isMusicSharingAvailable" -> _isMusicSharingAvailable.value = field.value as Boolean
                "isBluetoothICAvailable" -> _isBluetoothICAvailable.value = field.value as Boolean
                "isHFPMixingAvailable" -> _isHFPMixingAvailable.value = field.value as Boolean
                "isASRAvailable" -> _isASRAvailable.value = field.value as Boolean
                "isDMCAvailable" -> _isDMCAvailable.value = field.value as Boolean
                "numberOfSupportedICChannels" -> _numberOfSupportedICChannels.value = field.value as Int
                "isDynamicICAvailable" -> _isDynamicICAvailable.value = field.value as Boolean
                "isOTAAvailable" -> _isOTAAvailable.value = field.value as Boolean
                "isFMAvailable" -> _isFMAvailable.value = field.value as Boolean
                "isPrivateChatAvailable" -> _isPrivateChatAvailable.value = field.value as Boolean
                "isEcoModeAvailable" -> _isEcoModeAvailable.value = field.value as Boolean
                "isMobileBridgeAvailable" -> _isMobileBridgeAvailable.value = field.value as Boolean
                "isLRSpeakersAvailable" -> _isLRSpeakersAvailable.value = field.value as Boolean
                "isICDMCBridgeAvailable" -> _isICDMCBridgeAvailable.value = field.value as Boolean
                "isAutoOnOffAvailable" -> _isAutoOnOffAvailable.value = field.value as Boolean
                "isCSLNXTVADLicence" -> _isCSLNXTVADLicence.value = field.value as Boolean
                "isAdvancedMMIAvailable" -> _isAdvancedMMIAvailable.value = field.value as Boolean
                "deviceLetter" -> _deviceLetter.value = field.value as Int
                "version" -> _softwareVersion.value = field.value as Int
                "subVersion" -> _subVersion.value = field.value as Int
                "softwareRevision" -> _softwareRevision.value = field.value as Int
                "isDMCAGCEnabled" -> _isDMCAGCEnabled.value = field.value as Boolean
                "isAdvancedMMIEnabled" -> _isAdvancedMMIEnabled.value = field.value as Boolean
                "isRedialASREnabled" -> _isRedialASREnabled.value = field.value as Boolean
                "isRadioONASREnabled" -> _isRadioONASREnabled.value = field.value as Boolean
                "isAutoOnOffEnabled" -> _isAutoOnOffEnabled.value = field.value as Boolean
                "isAccessoriesActivated" -> _isAccessoriesActivated.value = field.value as Boolean
                "languageList" -> {
                    @Suppress("UNCHECKED_CAST")
                    _languageList.value = field.value as Set<CardoLanguage>
                }
                "equalizer Profiles" -> {
                    @Suppress("UNCHECKED_CAST")
                    _equalizerProfiles.value = field.value as Set<CardoEqualizerProfile>
                }
            }
        }
        if (decodedFields.any { it.name == "stat1" }) {
            _fmStationList.value = decodeFmStationList(decodedFields)
        }
    }
}