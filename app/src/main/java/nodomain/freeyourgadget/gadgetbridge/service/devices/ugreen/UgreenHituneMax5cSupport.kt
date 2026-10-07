package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_HIGH_QUALITY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_LANG
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_SPATIAL_AUDIO
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenAncMode
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenEqualizer
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenPromptLanguage
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import org.slf4j.LoggerFactory
import java.util.UUID

class UgreenHituneMax5cSupport : AbstractHeadphoneBTBRDeviceSupport(LOG, BUFFER_SIZE) {
    companion object {
        private val LOG = LoggerFactory.getLogger(UgreenHituneMax5cSupport::class.java)
        private const val BUFFER_SIZE = 1024
        private val SERIAL_PORT_SERVICE: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RFCOMM_CHANNEL = 1
    }

    private val protocol = UgreenProtocol()

    init {
        addSupportedService(SERIAL_PORT_SERVICE)
    }

    override fun useAutoConnect(): Boolean = false

    override fun getRfcommChannel(): Int = RFCOMM_CHANNEL

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        LOG.info("Initializing UGREEN HiTune Max 5c")
        builder.write(*UgreenProtocol.encodeGetDeviceState())
        builder.sleep(100)
        builder.write(*UgreenProtocol.encodeGetVersion())
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onSocketRead(data: ByteArray) {
        val events = protocol.processIncomingBytes(data)
        for (event in events) {
            evaluateGBDeviceEvent(event)
        }
    }

    override fun onSendConfiguration(config: String) {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        when (config) {
            PREF_UGREEN_ANC_MODE -> {
                val mode = UgreenAncMode.fromPreference(prefs.getString(config, null))
                val lastActive = prefs.getString(PREF_UGREEN_LAST_ACTIVE_ANC, null)?.let {
                    UgreenAncMode.fromPreference(it)
                }
                if (mode != UgreenAncMode.OFF && mode != UgreenAncMode.TRANSPARENCY) {
                    prefs.edit().putString(PREF_UGREEN_LAST_ACTIVE_ANC, mode.name.lowercase()).apply()
                }
                sendCommand("set anc mode", UgreenProtocol.encodeSetAnc(mode, lastActive))
            }
            PREF_UGREEN_EQ_PRESET -> {
                val eq = UgreenEqualizer.fromPreference(prefs.getString(config, null))
                sendCommand("set eq preset", UgreenProtocol.encodeSetEq(eq))
            }
            PREF_UGREEN_PROMPT_LANG -> {
                val lang = UgreenPromptLanguage.fromPreference(prefs.getString(config, null))
                sendCommand("set prompt language", UgreenProtocol.encodeSetPromptLang(lang))
            }
            PREF_UGREEN_PROMPT_VOLUME -> {
                val vol = prefs.getInt(config, 8)
                sendCommand("set prompt volume", UgreenProtocol.encodeSetPromptVolume(vol))
            }
            PREF_UGREEN_GAME_MODE -> {
                if (prefs.getBoolean(PREF_UGREEN_HIGH_QUALITY, false)) {
                    LOG.warn("Ignoring set game mode because high quality decoding is enabled")
                    return
                }
                val enabled = prefs.getBoolean(config, false)
                sendCommand("set game mode", UgreenProtocol.encodeSetGameMode(enabled))
            }
            PREF_UGREEN_DUAL_CONNECT -> {
                if (prefs.getBoolean(PREF_UGREEN_HIGH_QUALITY, false)) {
                    LOG.warn("Ignoring set dual connect because high quality decoding is enabled")
                    return
                }
                val enabled = prefs.getBoolean(config, false)
                sendCommand("set dual connect", UgreenProtocol.encodeSetDualConnect(enabled))
            }
            PREF_UGREEN_SPATIAL_AUDIO -> {
                val enabled = prefs.getBoolean(config, false)
                sendCommand("set spatial audio", UgreenProtocol.encodeSetSpatialAudio(enabled))
            }
            PREF_UGREEN_HIGH_QUALITY -> {
                val enabled = prefs.getBoolean(config, false)
                if (enabled) {
                    if (prefs.getBoolean(PREF_UGREEN_GAME_MODE, false)) {
                        prefs.edit().putBoolean(PREF_UGREEN_GAME_MODE, false).apply()
                    }
                    if (prefs.getBoolean(PREF_UGREEN_DUAL_CONNECT, false)) {
                        prefs.edit().putBoolean(PREF_UGREEN_DUAL_CONNECT, false).apply()
                    }
                }
                sendCommand("set high quality decoding", UgreenProtocol.encodeSetHighQuality(enabled))
            }
            else -> super.onSendConfiguration(config)
        }
    }

    override fun onFactoryReset() {
        sendCommand("factory reset", UgreenProtocol.encodeFactoryReset())
    }

    private fun sendCommand(taskName: String, payload: ByteArray) {
        val builder = createTransactionBuilder(taskName)
        builder.write(*payload)
        builder.queue()
    }
}
