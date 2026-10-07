package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEqualizerProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoLanguage
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoMicrophoneSensitivity


sealed class InfoType(val typeId: Int, val payloadLength: Int, val fields: List<CardoField<*>>) {

    fun containsFieldWithName(name: String): Boolean = fields.any { it.name == name }

    companion object {
        fun fromByte(b: Byte): InfoType {
            val id = b.toInt() and 0xFF
            return entries.find { it.typeId == id }
                ?: throw IllegalArgumentException("Unhandled Type: $id")
        }

        val entries: List<InfoType> by lazy {
            listOf(
                Config, Hardware, Volumes, Firmware, Software,
                LanguageList, ConfigOther, FmStation, Sensitivity,
                Features, AccessoriesActivate, EqualizerProfiles, Features2
            )
        }
    }

    object Config : InfoType(0x00, 3, listOf(
        enumField<CardoLanguage>("selectedLanguage", 4),
        BoolField("isASREnable"),
        BoolField("isVoicePromptsEnabled"),
        enumField<CardoFmRegion>("fmRegion", 1),
        BoolField("isFMRDSEnabled"),
        BoolField("isHFPMixingEnabled"),
        enumField<CardoMicrophoneSensitivity>("microphoneSensitivity", 2),
        IntField("agcSensitivity", 4),
        BoolField("isNoiseGateEnabled"),
        BoolField("isEcoModeEnabled"),
        BoolField("isDMCModeEnabled"),
        enumField<CardoEqualizerProfile>("equalizerProfile", 6)
    ))

    object Hardware : InfoType(0x01, 1, listOf(
        IntField("txPowerProfile", 4),
        BoolField("lrSpeakers")
    ))

    object Volumes : InfoType(0x02, 6, listOf(
        IntField("volumeID", 8),
        IntField("standByVolume", 4),
        IntField("groupingVolume", 4),
        IntField("ag1Volume", 4),
        IntField("ag2Volume", 4),
        IntField("fmVolume", 4),
        IntField("a2dp1Volume", 4),
        IntField("a2dp2Volume", 4),
        backgroundVolumeField("intercomBackgroundMusicVolume", 4), // customIndex, non ordinal!
        IntField("mixActiveSpeakerVolume", 8)
    ))

    object Firmware : InfoType(0x03, 6, listOf(
        IntField("headsetType", 16),
        IntField("major", 16),
        IntField("minor", 16)
    ))

    object Software : InfoType(0x04, 8, listOf(
        IntField("deviceLetter", 16),
        IntField("version", 16),
        IntField("subVersion", 16),
        IntField("softwareRevision", 16)
    ))

    object LanguageList : InfoType(0x05, 2, listOf(
        enumSetField<CardoLanguage>("languageList", 16)
    ))

    object ConfigOther : InfoType(0x06, 3, listOf(
        BoolField("isDMCAGCEnabled"),
        BoolField("isAdvancedMMIEnabled"),
        BoolField("isRedialASREnabled"),
        BoolField("isRadioONASREnabled"),
        BoolField("isAutoOnOffEnabled")
    ))

    object FmStation : InfoType(0x80, 13, listOf(
        IntField("stat", 8),
        IntField("stat1", 16),
        IntField("stat2", 16),
        IntField("stat3", 16),
        IntField("stat4", 16),
        IntField("stat5", 16),
        IntField("stat6", 16)
    ))

    object Sensitivity : InfoType(0x81, 4, listOf(
        IntField("sensitivity", 16)
    ))

    object Features : InfoType(0x82, 2, listOf(
        BoolField("isAutomaticVolumeAvailable"),
        BoolField("isMusicSharingAvailable"),
        BoolField("isBluetoothICAvailable"),
        BoolField("isHFPMixingAvailable"),
        BoolField("isASRAvailable"),
        BoolField("isDMCAvailable"),
        IntField("numberOfSupportedICChannels", 2),
        BoolField("isDynamicICAvailable"),
        BoolField("isOTAAvailable"),
        BoolField("isFMAvailable"),
        BoolField("isPrivateChatAvailable"),
        BoolField("isEcoModeAvailable"),
        BoolField("isMobileBridgeAvailable"),
        BoolField("isLRSpeakersAvailable"),
        BoolField("isICDMCBridgeAvailable")
    ))

    object AccessoriesActivate : InfoType(0x83, 1, listOf(
        BoolField("isAccessoriesActivated")
    ))

    object EqualizerProfiles : InfoType(0x84, 4, listOf(
        enumSetField<CardoEqualizerProfile>("equalizer Profiles", 32)
    ))

    object Features2 : InfoType(0x87, 3, listOf(
        IntField("skip", 5),
        BoolField("isAutoOnOffAvailable"),
        BoolField("isCSLNXTVADLicence"),
        BoolField("isAdvancedMMIAvailable")
    ))
}