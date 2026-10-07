package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.BatteryStatus

class CardoBatteryState {
    private val _level = MutableStateFlow(0)
    val level: StateFlow<Int> = _level.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    fun update(status: BatteryStatus) {
        _level.value = status.chargePercent
        _isCharging.value = status.isCharging
    }
}