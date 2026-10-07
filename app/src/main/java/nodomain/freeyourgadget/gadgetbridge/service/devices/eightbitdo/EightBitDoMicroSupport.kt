package nodomain.freeyourgadget.gadgetbridge.service.devices.eightbitdo

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.HidKey
import org.slf4j.LoggerFactory
import java.util.UUID

class EightBitDoMicroSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private var gotVersion = false
    private var gotKeymap = false
    private val keymapBuffer = ByteArray(EightBitDoProtocol.KEYMAP_SIZE)
    private val receivedChunks = mutableSetOf<Int>()
    private var heldButtons: Set<EightBitDoButton> = emptySet()

    init {
        addSupportedService(UUID_SERVICE)
    }

    override fun useAutoConnect(): Boolean = true

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        gotVersion = false
        gotKeymap = false
        keymapBuffer.fill(0)
        receivedChunks.clear()
        heldButtons = emptySet()

        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.requestMtu(200)
        builder.notify(UUID_CHARACTERISTIC, true)
        builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeHello())
        builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeFirmwareQuery())
        for (chunkOffset in EightBitDoProtocol.CHUNK_OFFSETS) {
            builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeKeymapRead(chunkOffset))
        }

        // The device is initialized once the version and the full keymap arrive

        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (characteristic.uuid == UUID_CHARACTERISTIC) {
            handleFrame(value)
            return true
        }

        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    private fun handleFrame(frame: ByteArray) {
        val response = EightBitDoProtocol.decodeResponse(frame)
        if (response == null) {
            LOG.warn("Failed to decode response {}", frame.toHexString())
            return
        }

        when (response.type) {
            EightBitDoProtocol.TYPE_FIRMWARE -> handleFirmwareVersion(response)
            EightBitDoProtocol.TYPE_KEYMAP_READ -> handleKeymapChunk(response)
            EightBitDoProtocol.TYPE_BUTTON_STATE -> handleButtonState(response)

            EightBitDoProtocol.TYPE_HELLO,
            EightBitDoProtocol.TYPE_KEYMAP_WRITE,
            EightBitDoProtocol.TYPE_KEYMAP_COMMIT,
            EightBitDoProtocol.TYPE_REALTIME -> LOG.debug(
                "Got ack for {} with status {}",
                response.type,
                response.status
            )

            else -> LOG.warn("Unhandled response type {}: {}", response.type, frame.toHexString())
        }

        if (gotVersion && gotKeymap && !device.isInitialized) {
            device.state = GBDevice.State.INITIALIZED
            device.sendDeviceUpdateIntent(context)
            setRealtime()
        }
    }

    private fun handleFirmwareVersion(response: EightBitDoProtocol.Response) {
        val version = EightBitDoProtocol.decodeFirmwareVersion(response)
        if (version == null) {
            LOG.warn("Failed to decode firmware version from {}", response)
            return
        }
        LOG.debug("Firmware version: {}", version)
        val event = GBDeviceEventVersionInfo()
        event.fwVersion = version
        evaluateGBDeviceEvent(event)
        gotVersion = true
    }

    private fun handleKeymapChunk(response: EightBitDoProtocol.Response) {
        val chunk = EightBitDoProtocol.decodeKeymapChunk(response)
        if (chunk == null) {
            LOG.warn("Failed to decode keymap chunk from {}", response)
            return
        }
        val (chunkOffset, data) = chunk
        data.copyInto(keymapBuffer, chunkOffset)
        receivedChunks.add(chunkOffset)
        if (!receivedChunks.containsAll(EightBitDoProtocol.CHUNK_OFFSETS)) {
            return
        }
        receivedChunks.clear()

        val keymap = EightBitDoProtocol.decodeKeymap(keymapBuffer)
        LOG.debug("Keymap: {}", keymap)

        val event = GBDeviceEventUpdatePreferences()
        event.withPreference(DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_DISABLE_SLEEP, keymap.disableSleep)
        for ((button, keys) in keymap.mapping) {
            event.withPreference(button.prefKey, encodeKeys(keys))
        }
        evaluateGBDeviceEvent(event)
        gotKeymap = true
    }

    private fun handleButtonState(response: EightBitDoProtocol.Response) {
        val buttons = EightBitDoProtocol.decodeButtonState(response)
        if (buttons == null) {
            LOG.warn("Failed to decode button state from {}", response)
            return
        }
        if (buttons != heldButtons) {
            LOG.info("Button state: {}", buttons)
            heldButtons = buttons
        }
    }

    override fun onSendConfiguration(config: String) {
        when {
            config == DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_REALTIME_BUTTONS -> setRealtime()

            config == DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_KEYMAP_RESET -> resetKeymap()

            config == DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_DISABLE_SLEEP ||
                config.startsWith(DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_KEYMAP_PREFIX) -> setKeymap()

            else -> super.onSendConfiguration(config)
        }
    }

    private fun setRealtime() {
        val enabled = devicePrefs.getBoolean(DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_REALTIME_BUTTONS, false)
        LOG.debug("Setting realtime button stream to {}", enabled)
        val builder = createTransactionBuilder("setRealtime")
        builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeRealtime(enabled))
        if (enabled) {
            builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeRealtimeEmptyButtonState())
        } else {
            heldButtons = emptySet()
        }
        builder.queue()
    }

    private fun resetKeymap() {
        LOG.info("Resetting keymap to defaults")
        val event = GBDeviceEventUpdatePreferences()
        for (button in EightBitDoButton.entries) {
            event.withPreference(button.prefKey, encodeKeys(listOfNotNull(button.defaultKey)))
        }
        evaluateGBDeviceEvent(event)
        setKeymap()
    }

    private fun setKeymap() {
        val prefs = devicePrefs
        val trimmedEvent = GBDeviceEventUpdatePreferences()
        var trimmed = false
        val mapping = EightBitDoButton.entries.associateWith { button ->
            val selected = decodeKeys(prefs.getString(button.prefKey, ""))
            val keys = EightBitDoProtocol.slotKeys(selected)
            if (keys.size != selected.size) {
                trimmed = true
                LOG.warn(
                    "Trimming {} to at most {} keys",
                    button,
                    EightBitDoProtocol.MAX_KEYS_PER_SLOT,
                )
                trimmedEvent.withPreference(button.prefKey, encodeKeys(keys))
            }
            keys
        }
        if (trimmed) {
            evaluateGBDeviceEvent(trimmedEvent)
        }

        val keymap = EightBitDoProtocol.Keymap(
            disableSleep = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_DISABLE_SLEEP, false),
            mapping = mapping,
        )
        LOG.debug("Setting keymap: {}", keymap)

        val buffer = EightBitDoProtocol.encodeKeymap(keymap)
        val builder = createTransactionBuilder("setKeymap")
        for (chunkOffset in EightBitDoProtocol.CHUNK_OFFSETS) {
            builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeKeymapWrite(chunkOffset, buffer))
        }
        builder.write(UUID_CHARACTERISTIC, *EightBitDoProtocol.encodeKeymapCommit())
        builder.queue()
    }

    private fun encodeKeys(keys: List<HidKey>): String = keys.joinToString(",") { it.prefValue }

    private fun decodeKeys(value: String): List<HidKey> =
        value.split(",").mapNotNull { HidKey.fromPrefValue(it) }

    companion object {
        private val LOG = LoggerFactory.getLogger(EightBitDoMicroSupport::class.java)

        val UUID_SERVICE: UUID = UUID.fromString("0000ff10-0000-1000-8000-00805f9b34fb")
        val UUID_CHARACTERISTIC: UUID = UUID.fromString("0000ff13-0000-1000-8000-00805f9b34fb")
    }
}
