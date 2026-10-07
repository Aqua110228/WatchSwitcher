package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import org.slf4j.LoggerFactory

class CardoResponse(incoming: ByteArray) {
    private val message: CardoMessage? = CardoMessage.getByCommand(incoming[0])

    val deviceEvents: MutableList<GBDeviceEvent> = mutableListOf()

    var friendlyName: String? = null
        private set
    var serialNumber: String? = null
        private set
    var firmwareInfo: FirmwareInfo? = null
        private set
    var batteryStatus: BatteryStatus? = null
        private set
    var deviceState: DeviceState? = null
        private set
    var configDecodedFields: List<DecodedField>? = null
        private set
    var subscribeState: SubscribeState? = null
        private set

    init {
        if (message != null) {
            val payload = if (message.hasLength)
                incoming.copyOfRange(2, 2 + (incoming[1].toInt() and 0xFF))
            else
                incoming.copyOfRange(1, incoming.size)

            when (message) {
                CardoMessage.CONFIG -> {
                    configDecodedFields = decodeConfigMessage(payload)

                    firmwareInfo = configDecodedFields
                        ?.takeIf { fields -> fields.any { it.name == "major" } }
                        ?.let { decodeFirmwareInfo(it) }

                    configDecodedFields
                        ?.takeIf { fields -> fields.any { it.name == "stat1" } }
                        ?.let { decodeFmStationList(it) }
                        ?.let { list ->
                            val preferencesEvent = GBDeviceEventUpdatePreferences()
                            list.frequencies.forEachIndexed { i, freq ->
                                preferencesEvent.withPreference("pref_cardo_fm_preset_${i + 1}", freq)
                            }
                            deviceEvents.add(preferencesEvent)
                        }
                }

                CardoMessage.SUBSCRIBE -> {
                    subscribeState = decodeSubscribeState(payload)
                }
                CardoMessage.BATTERY_STATUS -> {
                    val status = decodeBatteryStatus(payload)
                    batteryStatus = status

                    val battEvent = GBDeviceEventBatteryInfo()
                    battEvent.level = status.chargePercent
                    battEvent.state = if (status.isCharging) BatteryState.BATTERY_CHARGING else BatteryState.BATTERY_NORMAL
                    deviceEvents.add(battEvent)
                }
                CardoMessage.DEVICE_STATE -> {
                    val state = decodeDeviceState(payload)
                    deviceState = state

                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("state", state.state.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("callState", state.callState.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("callDirection", state.callDirection.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("fmState", state.fmState.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("currentSelectedIndex", state.currentSelectedIndex.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("currentStation", state.currentStation.toString()))
                    deviceEvents.add(GBDeviceEventUpdateDeviceInfo("dmcGroupState", state.dmcGroupState.toString()))

                    val preferencesEvent = GBDeviceEventUpdatePreferences()
                    preferencesEvent.withPreference("pref_cardo_fm_tuning", state.currentStation)
                    deviceEvents.add(preferencesEvent)
                }
                CardoMessage.DEVICE_ALIAS -> {
                    friendlyName = decodeString(payload)
                }

                CardoMessage.DEVICE_SERIAL_NUMBER -> {
                    serialNumber = decodeString(payload)
                }
                else -> {
                    LOG.debug("INCOMING MESSAGE TYPE {} PAYLOAD {}", message, incoming)
                }
            }
        }
    }

    private fun decodeString(payload: ByteArray): String = String(payload, Charsets.UTF_8)

    companion object {
        private val LOG = LoggerFactory.getLogger(CardoResponse::class.java)
    }
}