package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums

import org.apache.commons.lang3.EnumUtils

interface CardoEnums {
    val btPayload: Int
}

enum class CardoState : CardoEnums {
    START, STAND_BY, PAIRING, MOBILE_CALL, MUSIC_ACTIVE, FM_ACTIVE;

    override val btPayload: Int get() = ordinal
}

enum class CardoCallState : CardoEnums {
    IDLE, ESTABLISHING, ACTIVE_SCO, HOLD, WAITING_3_WAY, ACTIVE_3_WAY, ACTIVE_NO_SCO;
    override val btPayload: Int get() = ordinal
}

enum class CardoCallDirection : CardoEnums {
    UNKNOWN, OUTGOING, INCOMING;
    override val btPayload: Int get() = ordinal
}

enum class CardoFmState : CardoEnums {
    IDLE, ACTIVE, SCANNING_FORWARD, SCANNING_BACKWARD, AUTOTUNE_FORWARD, AUTOTUNE_BACKWARD;
    override val btPayload: Int get() = ordinal
}

enum class CardoDmcGroupState : CardoEnums {
    READY, TALK, MUTE;
    override val btPayload: Int get() = ordinal
}

enum class CardoFmRegion(val minFreq: Int, val maxFreq: Int) : CardoEnums {
    WORLDWIDE(8700, 10800),
    JAPAN(7600, 9500);

    override val btPayload: Int get() = ordinal
}

enum class CardoLanguage : CardoEnums {
    ENGLISH_US, ENGLISH_UK, SPANISH, FRENCH, DEUTSCH, JAPANESE,
    CHINESE, UNK_7, ITALIAN, RUSSIAN, HEBREW, PORTUGUESE, KOREAN;

    override val btPayload: Int get() = ordinal

    companion object {
        @JvmStatic
        fun fromBitMask(code: Int): Set<CardoLanguage> =
            EnumUtils.processBitVector(CardoLanguage::class.java, code.toLong())
    }
}

enum class CardoMicrophoneSensitivity : CardoEnums {
    LOW, MEDIUM, HIGH;

    override val btPayload: Int get() = ordinal
}

enum class CardoBackgroundVolume(val customIndex: Int) : CardoEnums {
    PERCENT_10(9), PERCENT_20(1), PERCENT_30(2), PERCENT_40(3), PERCENT_50(4),
    PERCENT_60(5), PERCENT_70(6), PERCENT_80(7), PERCENT_90(8), PERCENT_100(0);

    override val btPayload: Int get() = customIndex

    companion object {
        @JvmStatic
        fun fromCustomIndex(index: Int): CardoBackgroundVolume =
            values().find { it.customIndex == index }
                ?: throw IllegalArgumentException("Invalid index: $index")
    }
}

enum class CardoEqualizerProfile : CardoEnums {
    HIGH_VOLUME, BASS_BOOST, VOCAL, OFF,
    UNK_4, UNK_5, UNK_6, UNK_7, UNK_8, UNK_9, UNK_10, UNK_11, UNK_12, UNK_13, UNK_14, UNK_15,
    JBL_HIGH_VOLUME, JBL_BASS_BOOST, JBL_VOCAL, JBL_OFF;

    override val btPayload: Int get() = ordinal

    companion object {
        @JvmStatic
        fun fromBitMask(code: Int): Set<CardoEqualizerProfile> =
            EnumUtils.processBitVector(CardoEqualizerProfile::class.java, code.toLong())
    }
}
