package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBLEDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.CardoResponse
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.CardoBLEProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.buildSetRequests
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.FmTuneMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state.CardoDeviceStatus
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class CardoDeviceSupport : AbstractHeadphoneBLEDeviceSupport(LOG) {

    private val typedStatus = CardoDeviceStatus()
    private val cardoBLEProfile: CardoBLEProfile<CardoDeviceSupport> = CardoBLEProfile(this)

    init {
        addSupportedService(SERVICE_UUID)
        addSupportedProfile(cardoBLEProfile)
    }

    override fun onFactoryReset() {
        cardoBLEProfile.factoryReset()
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        typedStatus.restoreFrom(GBApplication.getDeviceSpecificSharedPrefs(device.address))

        builder.add(SetDeviceStateAction(device, GBDevice.State.INITIALIZING, context))
        device.setFirmwareVersion("N/A")
        device.setFirmwareVersion2("N/A")
        builder.requestMtu(64)
        builder.notify(getCharacteristic(UUID_READ_CHARACTERISTIC), true)
        builder.add(SetDeviceStateAction(device, GBDevice.State.INITIALIZED, context))

        cardoBLEProfile.initialize(builder)
        cardoBLEProfile.getFirmwareVersion(builder)
        cardoBLEProfile.subscribe(builder)
        return builder
    }

    override fun onSendConfiguration(config: String) {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)

        when (config) {
            "pref_cardo_toggle_fm" ->
                cardoBLEProfile.toggleFmRadioPower(prefs.getBoolean("pref_cardo_toggle_fm", false))
            "pref_cardo_toggle_voice_prompts" -> {
                val enabled = prefs.getBoolean("pref_cardo_toggle_voice_prompts", false)
                val currentValues = typedStatus.config.toFieldValueMap()
                val requests = buildSetRequests("isVoicePromptsEnabled", enabled, currentValues)
                for (req in requests) {
                    cardoBLEProfile.sendOutgoingRequest("Send request", req)
                }
            }
            "pref_cardo_standby_volume" -> {
                val newValue = prefs.getInt("pref_cardo_standby_volume", 0)
                val currentValues = typedStatus.config.toFieldValueMap()
                val requests = buildSetRequests("standByVolume", newValue, currentValues)
                for (req in requests) {
                    cardoBLEProfile.sendOutgoingRequest("Send request", req)
                }
            }
            "pref_cardo_fm_tuning" -> {
                val rawTuning = prefs.getInt("pref_cardo_fm_tuning", 0)
                LOG.debug("slider value: {}", rawTuning)
                cardoBLEProfile.tune(FmTuneMode.Frequency(rawTuning, typedStatus.config.fmRegion.value))
            }
            "fake_seek_up" -> cardoBLEProfile.tune(FmTuneMode.Simple.SEEK_UP)
            "fake_scan_up" -> cardoBLEProfile.tune(FmTuneMode.Simple.SCAN_UP)
            "fake_seek_down" -> cardoBLEProfile.tune(FmTuneMode.Simple.SEEK_DOWN)
            "fake_scan_down" -> cardoBLEProfile.tune(FmTuneMode.Simple.SCAN_DOWN)
            "fake_scan_stop" -> cardoBLEProfile.tune(FmTuneMode.Simple.STOP_SCAN)
            "fake_autotune" -> cardoBLEProfile.tune(FmTuneMode.Simple.AUTO_TUNE)
            "pref_cardo_fm_preset" -> {
                val index = prefs.getString("pref_cardo_fm_preset", "1")?.toIntOrNull() ?: 1
                cardoBLEProfile.tune(FmTuneMode.Preset(index))
            }
            else -> {
                LOG.debug("CONFIG: $config")
            }
        }

        super.onSendConfiguration(config)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        super.onCharacteristicChanged(gatt, characteristic, value)

        val characteristicUUID = characteristic.uuid

        if (UUID_READ_CHARACTERISTIC == characteristicUUID) {
            val response = CardoResponse(value)

            response.batteryStatus?.let { typedStatus.battery.update(it) }
            response.deviceState?.let { typedStatus.call.update(it) }
            response.configDecodedFields?.let {
                typedStatus.config.update(it)
                typedStatus.capabilities.update(it)
            }
            response.friendlyName?.let { typedStatus.deviceInfo.updateFriendlyName(it) }
            response.serialNumber?.let { typedStatus.deviceInfo.updateSerialNumber(it) }
            response.firmwareInfo?.let { typedStatus.deviceInfo.updateFirmwareInfo(it) }
            response.subscribeState?.let { typedStatus.subscribe.update(it.services, it.specificServices) }

            for (deviceEvent in response.deviceEvents) {
                evaluateGBDeviceEvent(deviceEvent)
            }

            LOG.debug("INCOMING msg: {}", GB.hexdump(value))
            return true
        }

        LOG.warn("Unhandled read {} -> {}", characteristicUUID, GB.hexdump(value))
        return false
    }

    override fun onTestNewFunction(options: Bundle?) {
        cardoBLEProfile.tune(FmTuneMode.Preset(6))
    }

    override fun useAutoConnect(): Boolean = true

    companion object {

        private val LOG: Logger = LoggerFactory.getLogger(CardoDeviceSupport::class.java)
        private val SERVICE_UUID: UUID = UUID.fromString("CD007F80-8B0B-11E6-AE22-56B6B6499611")
        private val UUID_READ_CHARACTERISTIC: UUID = UUID.fromString("cd007f82-8b0b-11e6-ae22-56b6b6499611")
    }
}
