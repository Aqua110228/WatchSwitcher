package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallDirection
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoDmcGroupState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitReader
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.IntField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.enumField
import org.jetbrains.annotations.TestOnly

data class DeviceState(
    val state: CardoState,
    val callState: CardoCallState,
    val callDirection: CardoCallDirection,
    val fmState: CardoFmState,
    val currentSelectedIndex: Int,
    val currentStation: Int,
    val dmcGroupState: CardoDmcGroupState
)

private val stateField = enumField<CardoState>("state", 8)
private val callDirectionField = enumField<CardoCallDirection>("callDirection", 2)
private val callStateField = enumField<CardoCallState>("callState", 6)
private val fmStateField = enumField<CardoFmState>("fmState", 4)
private val currentSelectedIndexField = IntField("currentSelectedIndex", 4)
private val currentStationField = IntField("currentStation", 16)
private val dmcGroupStateField = enumField<CardoDmcGroupState>("dmcGroupState", 2)

fun decodeDeviceState(payload: ByteArray): DeviceState {
    val reader = BitReader(payload)
    return DeviceState(
        state = reader.read(stateField),
        callDirection = reader.read(callDirectionField),
        callState = reader.read(callStateField),
        fmState = reader.read(fmStateField),
        currentSelectedIndex = reader.read(currentSelectedIndexField),
        currentStation = reader.read(currentStationField),
        dmcGroupState = reader.read(dmcGroupStateField)
    )
}

@TestOnly
fun deviceStateToString(state: DeviceState): String = buildString {
    append("state - ${state.state}").append(System.lineSeparator())
    append("callState - ${state.callState}").append(System.lineSeparator())
    append("callDirection - ${state.callDirection}").append(System.lineSeparator())
    append("fmState - ${state.fmState}").append(System.lineSeparator())
    append("currentSelectedIndex - ${state.currentSelectedIndex}").append(System.lineSeparator())
    append("currentStation - ${state.currentStation}").append(System.lineSeparator())
    append("dmcGroupState - ${state.dmcGroupState}").append(System.lineSeparator())
}
