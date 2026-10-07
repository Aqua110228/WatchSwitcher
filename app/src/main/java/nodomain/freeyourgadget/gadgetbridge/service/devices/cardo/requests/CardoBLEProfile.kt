package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests

import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.AbstractBleProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.CardoDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.Services
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.SpecificServices
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.FmTuneMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioToggle
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioTune
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.InfoType
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.apache.commons.lang3.EnumUtils
import org.slf4j.LoggerFactory
import java.util.UUID

class CardoBLEProfile<T : AbstractBTLESingleDeviceSupport>(support: T) : AbstractBleProfile<T>(support) {

    private val cardoDeviceSupport = support as CardoDeviceSupport

    fun initialize(builder: TransactionBuilder) {
        builder.write(getCharacteristic(UUID_WRITE_CHARACTERISTIC), *InitRequest().toByteArray())
    }

    fun getFirmwareVersion(builder: TransactionBuilder) {
        val infoTypeIds = listOf(
            InfoType.Firmware.typeId,
            InfoType.LanguageList.typeId,
            InfoType.Volumes.typeId,
            InfoType.Config.typeId
        )
        builder.write(getCharacteristic(UUID_WRITE_CHARACTERISTIC), *GetRequest(infoTypeIds).toByteArray())
    }
    fun subscribe(builder: TransactionBuilder) {
        val servicesMask = EnumUtils.generateBitVector(
            Services::class.java, Services.knownValues()
        ).toInt()
        val specificServicesMask = EnumUtils.generateBitVector(
            SpecificServices::class.java, SpecificServices.knownValues()
        ).toInt()
        val request = SubscribeRequest(servicesMask, specificServicesMask)
        builder.write(getCharacteristic(UUID_WRITE_CHARACTERISTIC), *request.toByteArray())
    }

    fun toggleFmRadioPower(on: Boolean) = sendOutgoingRequest("toggle Fm Radio", fmRadioToggle(on))

    fun tune(mode: FmTuneMode) = sendOutgoingRequest("tune operation", fmRadioTune(mode))

    fun factoryReset() = sendOutgoingRequest("factory reset", FactoryResetRequest())

    fun sendOutgoingRequest(taskName: String, message: CardoOutgoingMessage) {
        val builder = cardoDeviceSupport.createTransactionBuilder(taskName)
        val btMessage = message.toByteArray()
        LOG.debug("SENDING {}: {}", taskName, GB.hexdump(btMessage))
        builder.write(getCharacteristic(UUID_WRITE_CHARACTERISTIC), *btMessage)
        builder.queue()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CardoBLEProfile::class.java)
        private val UUID_WRITE_CHARACTERISTIC: UUID = UUID.fromString("cd007f81-8b0b-11e6-ae22-56b6b6499611")
    }
}