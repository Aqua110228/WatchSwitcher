package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoBackgroundVolume
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEqualizerProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoLanguage
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoMicrophoneSensitivity
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.DecodedField

class CardoConfigState {
    private val _selectedLanguage = MutableStateFlow(CardoLanguage.ENGLISH_US)
    val selectedLanguage: StateFlow<CardoLanguage> = _selectedLanguage.asStateFlow()

    private val _isASREnable = MutableStateFlow(false)
    val isASREnable: StateFlow<Boolean> = _isASREnable.asStateFlow()

    private val _isVoicePromptsEnabled = MutableStateFlow(false)
    val isVoicePromptsEnabled: StateFlow<Boolean> = _isVoicePromptsEnabled.asStateFlow()

    private val _fmRegion = MutableStateFlow(CardoFmRegion.WORLDWIDE)
    val fmRegion: StateFlow<CardoFmRegion> = _fmRegion.asStateFlow()

    private val _isFMRDSEnabled = MutableStateFlow(false)
    val isFMRDSEnabled: StateFlow<Boolean> = _isFMRDSEnabled.asStateFlow()

    private val _isHFPMixingEnabled = MutableStateFlow(false)
    val isHFPMixingEnabled: StateFlow<Boolean> = _isHFPMixingEnabled.asStateFlow()

    private val _microphoneSensitivity = MutableStateFlow(CardoMicrophoneSensitivity.LOW)
    val microphoneSensitivity: StateFlow<CardoMicrophoneSensitivity> = _microphoneSensitivity.asStateFlow()

    private val _agcSensitivity = MutableStateFlow(0)
    val agcSensitivity: StateFlow<Int> = _agcSensitivity.asStateFlow()

    private val _isNoiseGateEnabled = MutableStateFlow(false)
    val isNoiseGateEnabled: StateFlow<Boolean> = _isNoiseGateEnabled.asStateFlow()

    private val _isEcoModeEnabled = MutableStateFlow(false)
    val isEcoModeEnabled: StateFlow<Boolean> = _isEcoModeEnabled.asStateFlow()

    private val _isDMCModeEnabled = MutableStateFlow(false)
    val isDMCModeEnabled: StateFlow<Boolean> = _isDMCModeEnabled.asStateFlow()

    private val _equalizerProfile = MutableStateFlow(CardoEqualizerProfile.OFF)
    val equalizerProfile: StateFlow<CardoEqualizerProfile> = _equalizerProfile.asStateFlow()

    private val _volumeID = MutableStateFlow(0)
    val volumeID: StateFlow<Int> = _volumeID.asStateFlow()

    private val _standByVolume = MutableStateFlow(0)
    val standByVolume: StateFlow<Int> = _standByVolume.asStateFlow()

    private val _groupingVolume = MutableStateFlow(0)
    val groupingVolume: StateFlow<Int> = _groupingVolume.asStateFlow()

    private val _ag1Volume = MutableStateFlow(0)
    val ag1Volume: StateFlow<Int> = _ag1Volume.asStateFlow()

    private val _ag2Volume = MutableStateFlow(0)
    val ag2Volume: StateFlow<Int> = _ag2Volume.asStateFlow()

    private val _fmVolume = MutableStateFlow(0)
    val fmVolume: StateFlow<Int> = _fmVolume.asStateFlow()

    private val _a2dp1Volume = MutableStateFlow(0)
    val a2dp1Volume: StateFlow<Int> = _a2dp1Volume.asStateFlow()

    private val _a2dp2Volume = MutableStateFlow(0)
    val a2dp2Volume: StateFlow<Int> = _a2dp2Volume.asStateFlow()

    private val _intercomBackgroundMusicVolume = MutableStateFlow(CardoBackgroundVolume.PERCENT_100)
    val intercomBackgroundMusicVolume: StateFlow<CardoBackgroundVolume> = _intercomBackgroundMusicVolume.asStateFlow()

    private val _mixActiveSpeakerVolume = MutableStateFlow(0)
    val mixActiveSpeakerVolume: StateFlow<Int> = _mixActiveSpeakerVolume.asStateFlow()

    fun update(decodedFields: List<DecodedField>) {
        for (field in decodedFields) {
            when (field.name) {
                "selectedLanguage" -> _selectedLanguage.value = field.value as CardoLanguage
                "isASREnable" -> _isASREnable.value = field.value as Boolean
                "isVoicePromptsEnabled" -> _isVoicePromptsEnabled.value = field.value as Boolean
                "fmRegion" -> _fmRegion.value = field.value as CardoFmRegion
                "isFMRDSEnabled" -> _isFMRDSEnabled.value = field.value as Boolean
                "isHFPMixingEnabled" -> _isHFPMixingEnabled.value = field.value as Boolean
                "microphoneSensitivity" -> _microphoneSensitivity.value = field.value as CardoMicrophoneSensitivity
                "agcSensitivity" -> _agcSensitivity.value = field.value as Int
                "isNoiseGateEnabled" -> _isNoiseGateEnabled.value = field.value as Boolean
                "isEcoModeEnabled" -> _isEcoModeEnabled.value = field.value as Boolean
                "isDMCModeEnabled" -> _isDMCModeEnabled.value = field.value as Boolean
                "equalizerProfile" -> _equalizerProfile.value = field.value as CardoEqualizerProfile
                "volumeID" -> _volumeID.value = field.value as Int
                "standByVolume" -> _standByVolume.value = field.value as Int
                "groupingVolume" -> _groupingVolume.value = field.value as Int
                "ag1Volume" -> _ag1Volume.value = field.value as Int
                "ag2Volume" -> _ag2Volume.value = field.value as Int
                "fmVolume" -> _fmVolume.value = field.value as Int
                "a2dp1Volume" -> _a2dp1Volume.value = field.value as Int
                "a2dp2Volume" -> _a2dp2Volume.value = field.value as Int
                "intercomBackgroundMusicVolume" -> _intercomBackgroundMusicVolume.value = field.value as CardoBackgroundVolume
                "mixActiveSpeakerVolume" -> _mixActiveSpeakerVolume.value = field.value as Int
            }
        }
    }

    fun toFieldValueMap(): Map<String, Any> = mapOf(
        "selectedLanguage" to selectedLanguage.value,
        "isASREnable" to isASREnable.value,
        "isVoicePromptsEnabled" to isVoicePromptsEnabled.value,
        "fmRegion" to fmRegion.value,
        "isFMRDSEnabled" to isFMRDSEnabled.value,
        "isHFPMixingEnabled" to isHFPMixingEnabled.value,
        "microphoneSensitivity" to microphoneSensitivity.value,
        "agcSensitivity" to agcSensitivity.value,
        "isNoiseGateEnabled" to isNoiseGateEnabled.value,
        "isEcoModeEnabled" to isEcoModeEnabled.value,
        "isDMCModeEnabled" to isDMCModeEnabled.value,
        "equalizerProfile" to equalizerProfile.value,
        "volumeID" to volumeID.value,
        "standByVolume" to standByVolume.value,
        "groupingVolume" to groupingVolume.value,
        "ag1Volume" to ag1Volume.value,
        "ag2Volume" to ag2Volume.value,
        "fmVolume" to fmVolume.value,
        "a2dp1Volume" to a2dp1Volume.value,
        "a2dp2Volume" to a2dp2Volume.value,
        "intercomBackgroundMusicVolume" to intercomBackgroundMusicVolume.value,
        "mixActiveSpeakerVolume" to mixActiveSpeakerVolume.value
    )

    fun toPreferencesMap(): Map<String, Any> = mapOf(
        "CardoConfigState_selectedLanguage" to selectedLanguage.value.toPrefString(),
        "CardoConfigState_isASREnable" to isASREnable.value,
        "CardoConfigState_isVoicePromptsEnabled" to isVoicePromptsEnabled.value,
        "CardoConfigState_fmRegion" to fmRegion.value.toPrefString(),
        "CardoConfigState_isFMRDSEnabled" to isFMRDSEnabled.value,
        "CardoConfigState_isHFPMixingEnabled" to isHFPMixingEnabled.value,
        "CardoConfigState_microphoneSensitivity" to microphoneSensitivity.value.toPrefString(),
        "CardoConfigState_agcSensitivity" to agcSensitivity.value,
        "CardoConfigState_isNoiseGateEnabled" to isNoiseGateEnabled.value,
        "CardoConfigState_isEcoModeEnabled" to isEcoModeEnabled.value,
        "CardoConfigState_isDMCModeEnabled" to isDMCModeEnabled.value,
        "CardoConfigState_equalizerProfile" to equalizerProfile.value.toPrefString(),
        "CardoConfigState_volumeID" to volumeID.value,
        "CardoConfigState_standByVolume" to standByVolume.value,
        "CardoConfigState_groupingVolume" to groupingVolume.value,
        "CardoConfigState_ag1Volume" to ag1Volume.value,
        "CardoConfigState_ag2Volume" to ag2Volume.value,
        "CardoConfigState_fmVolume" to fmVolume.value,
        "CardoConfigState_a2dp1Volume" to a2dp1Volume.value,
        "CardoConfigState_a2dp2Volume" to a2dp2Volume.value,
        "CardoConfigState_intercomBackgroundMusicVolume" to intercomBackgroundMusicVolume.value.toPrefString(),
        "CardoConfigState_mixActiveSpeakerVolume" to mixActiveSpeakerVolume.value
    )

    fun restoreFrom(prefs: SharedPreferences) {
        _selectedLanguage.value = prefs.getEnumOrDefault("CardoConfigState_selectedLanguage", _selectedLanguage.value)
        _isASREnable.value = prefs.getBoolean("CardoConfigState_isASREnable", _isASREnable.value)
        _isVoicePromptsEnabled.value = prefs.getBoolean("CardoConfigState_isVoicePromptsEnabled", _isVoicePromptsEnabled.value)
        _fmRegion.value = prefs.getEnumOrDefault("CardoConfigState_fmRegion", _fmRegion.value)
        _isFMRDSEnabled.value = prefs.getBoolean("CardoConfigState_isFMRDSEnabled", _isFMRDSEnabled.value)
        _isHFPMixingEnabled.value = prefs.getBoolean("CardoConfigState_isHFPMixingEnabled", _isHFPMixingEnabled.value)
        _microphoneSensitivity.value = prefs.getEnumOrDefault("CardoConfigState_microphoneSensitivity", _microphoneSensitivity.value)
        _agcSensitivity.value = prefs.getInt("CardoConfigState_agcSensitivity", _agcSensitivity.value)
        _isNoiseGateEnabled.value = prefs.getBoolean("CardoConfigState_isNoiseGateEnabled", _isNoiseGateEnabled.value)
        _isEcoModeEnabled.value = prefs.getBoolean("CardoConfigState_isEcoModeEnabled", _isEcoModeEnabled.value)
        _isDMCModeEnabled.value = prefs.getBoolean("CardoConfigState_isDMCModeEnabled", _isDMCModeEnabled.value)
        _equalizerProfile.value = prefs.getEnumOrDefault("CardoConfigState_equalizerProfile", _equalizerProfile.value)
        _volumeID.value = prefs.getInt("CardoConfigState_volumeID", _volumeID.value)
        _standByVolume.value = prefs.getInt("CardoConfigState_standByVolume", _standByVolume.value)
        _groupingVolume.value = prefs.getInt("CardoConfigState_groupingVolume", _groupingVolume.value)
        _ag1Volume.value = prefs.getInt("CardoConfigState_ag1Volume", _ag1Volume.value)
        _ag2Volume.value = prefs.getInt("CardoConfigState_ag2Volume", _ag2Volume.value)
        _fmVolume.value = prefs.getInt("CardoConfigState_fmVolume", _fmVolume.value)
        _a2dp1Volume.value = prefs.getInt("CardoConfigState_a2dp1Volume", _a2dp1Volume.value)
        _a2dp2Volume.value = prefs.getInt("CardoConfigState_a2dp2Volume", _a2dp2Volume.value)
        _intercomBackgroundMusicVolume.value = prefs.getEnumOrDefault("CardoConfigState_intercomBackgroundMusicVolume", _intercomBackgroundMusicVolume.value)
        _mixActiveSpeakerVolume.value = prefs.getInt("CardoConfigState_mixActiveSpeakerVolume", _mixActiveSpeakerVolume.value)
    }
}
