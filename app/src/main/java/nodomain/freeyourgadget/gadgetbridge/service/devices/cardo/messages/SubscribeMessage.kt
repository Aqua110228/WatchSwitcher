package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitReader
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.enumSetField
import org.apache.commons.lang3.EnumUtils
import org.jetbrains.annotations.TestOnly
import java.util.EnumSet

private val servicesField = enumSetField<Services>("services", 8)
private val specificServicesField = enumSetField<SpecificServices>("specific services", 16)

data class SubscribeState(val services: Set<Services>, val specificServices: Set<SpecificServices>)

fun decodeSubscribeState(payload: ByteArray): SubscribeState {
    val reader = BitReader(payload)
    return SubscribeState(
        services = reader.read(servicesField),
        specificServices = reader.read(specificServicesField)
    )
}

@TestOnly
fun subscribeStateToString(state: SubscribeState): String = buildString {
    append("services - ${state.services}").append(System.lineSeparator())
    append("specific services - ${state.specificServices}").append(System.lineSeparator())
}

enum class Services {
    CAIP_SRVC_UPDATE, NA_1, NA_2, NA_3, CAIP_SRVC_DISCONNECT, UNK_5, CAIP_SRVC_BATTERY, CAIP_SRVC_STATE;

    companion object {
        fun fromBitMask(code: Int): EnumSet<Services> = EnumUtils.processBitVector(Services::class.java, code.toLong())
        fun knownValues(): EnumSet<Services> =
            entries.filterNot { it.name.startsWith("NA_") }.let {
                if (it.isEmpty()) EnumSet.noneOf(Services::class.java) else EnumSet.copyOf(it)
            }
    }
}

enum class SpecificServices {
    NA_0, NA_1, NA_2, NA_3, NA_4, NA_5, NA_6,
    CAIP_SRVC_MUSIC_SHARING, CAIP_SRVC_IC_MODULE_STATE, CAIP_SRVC_DIRECT_IC_STATE,
    CAIP_SRVC_BRIDGE, CAIP_SRVC_UNICAST, CAIP_SRVC_DMC_GROUP_EVENT, CAIP_SRVC_DMC_TOPO,
    CAIP_SRVC_GROUP_NAME, CAIP_SRVC_GROUP_HEADER;

    companion object {
        fun fromBitMask(code: Int): EnumSet<SpecificServices> = EnumUtils.processBitVector(SpecificServices::class.java, code.toLong())
        fun knownValues(): EnumSet<SpecificServices> =
            entries.filterNot { it.name.startsWith("NA_") }.let {
                if (it.isEmpty()) EnumSet.noneOf(SpecificServices::class.java) else EnumSet.copyOf(it)
            }
    }
}
