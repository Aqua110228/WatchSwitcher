package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallDirection
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoDmcGroupState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.DeviceState

class CardoCallStatus {
    private val _state = MutableStateFlow(CardoState.START)
    val state: StateFlow<CardoState> = _state.asStateFlow()

    private val _callState = MutableStateFlow(CardoCallState.IDLE)
    val callState: StateFlow<CardoCallState> = _callState.asStateFlow()

    private val _callDirection = MutableStateFlow(CardoCallDirection.UNKNOWN)
    val callDirection: StateFlow<CardoCallDirection> = _callDirection.asStateFlow()

    private val _fmState = MutableStateFlow(CardoFmState.IDLE)
    val fmState: StateFlow<CardoFmState> = _fmState.asStateFlow()

    private val _currentSelectedIndex = MutableStateFlow(0)
    val currentSelectedIndex: StateFlow<Int> = _currentSelectedIndex.asStateFlow()

    private val _currentStation = MutableStateFlow(0)
    val currentStation: StateFlow<Int> = _currentStation.asStateFlow()

    private val _dmcGroupState = MutableStateFlow(CardoDmcGroupState.READY)
    val dmcGroupState: StateFlow<CardoDmcGroupState> = _dmcGroupState.asStateFlow()

    fun update(deviceState: DeviceState) {
        _state.value = deviceState.state
        _callState.value = deviceState.callState
        _callDirection.value = deviceState.callDirection
        _fmState.value = deviceState.fmState
        _currentSelectedIndex.value = deviceState.currentSelectedIndex
        _currentStation.value = deviceState.currentStation
        _dmcGroupState.value = deviceState.dmcGroupState
    }
}