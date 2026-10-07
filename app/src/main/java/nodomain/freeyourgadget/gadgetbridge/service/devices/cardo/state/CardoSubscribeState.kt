package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.Services
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.SpecificServices

class CardoSubscribeState {
    private val _services = MutableStateFlow<Set<Services>>(emptySet())
    val services: StateFlow<Set<Services>> = _services.asStateFlow()

    private val _specificServices = MutableStateFlow<Set<SpecificServices>>(emptySet())
    val specificServices: StateFlow<Set<SpecificServices>> = _specificServices.asStateFlow()

    fun update(services: Set<Services>, specificServices: Set<SpecificServices>) {
        _services.value = services
        _specificServices.value = specificServices
    }
}
