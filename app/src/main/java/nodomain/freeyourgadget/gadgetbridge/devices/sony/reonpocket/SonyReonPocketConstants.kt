/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket

import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object SonyReonPocketConstants {
    const val ACTION_TOGGLE_AUTO_COOLING = "toggle_auto_cooling"
    const val ACTION_UNPAIR = "unpair"
    const val ACTION_TOGGLE_HEAT_COLD = "toggle_heat_cold"
    const val ACTION_CYCLE_POWER = "cycle_power"
    const val ACTION_REGISTER_TAG = "register_tag"
    const val ACTION_UNREGISTER_TAG = "unregister_tag"

    const val PREF_SMART_MODE_ENABLED = "pref_sony_reon_pocket_smart_mode_enabled"
    const val PREF_SMART_ACTIVE = "pref_sony_reon_pocket_smart_active"
    const val PREF_SMART_AUTO = "pref_sony_reon_pocket_smart_auto"
    const val PREF_AUTH_KEY = "pref_sony_reon_pocket_auth_key"
    const val PREF_HEAT_MODE = "pref_sony_reon_pocket_heat_mode"
    const val PREF_AUTO_START_STOP = "pref_sony_reon_pocket_auto_start_stop"
    const val PREF_INTENSITY = "pref_sony_reon_pocket_intensity"
    const val PREF_INTENSITY_COOL = "pref_sony_reon_pocket_intensity_cool"
    const val PREF_INTENSITY_HEAT = "pref_sony_reon_pocket_intensity_heat"
    const val PREF_MANUAL_POWER = "pref_sony_reon_pocket_manual_power"
    const val PREF_MANUAL_POWER_LAST = "pref_sony_reon_pocket_manual_power_last"

    const val PREF_PANEL_TEMPERATURE = "pref_sony_reon_pocket_panel_temperature"
    const val PREF_HEAT_DISSIPATION = "pref_sony_reon_pocket_heat_dissipation"

    // Reon Tag (RNPT-1) relayed through the Reon Pocket Pro.
    const val PREF_TAG_ADDRESS = "pref_sony_reon_pocket_tag_address"
    const val PREF_TAG_SERIAL = "pref_sony_reon_pocket_tag_serial"
    const val PREF_TAG_TEMPERATURE = "pref_sony_reon_pocket_tag_temperature"
    const val PREF_TAG_HUMIDITY = "pref_sony_reon_pocket_tag_humidity"
    const val PREF_TAG_BATTERY = "pref_sony_reon_pocket_tag_battery"

    // Smart auto switching thresholds (degrees Celsius, 0.5 steps). Defaults match a captured value.
    const val PREF_SMART_AUTO_THRESHOLD_COOL = "pref_sony_reon_pocket_smart_auto_threshold_cool"
    const val PREF_SMART_AUTO_THRESHOLD_WARM = "pref_sony_reon_pocket_smart_auto_threshold_warm"

    // Random salt mixed into the Owner ID to avoid deriving key from the same device MAC.
    // Basically, avoid that different Gadgetbridge users can claim the same device.
    const val PREF_OWNER_ID_SALT = "pref_sony_reon_pocket_owner_id_salt"

    val SERVICE_UUID: UUID = UUID.fromString("04ca1501-fd57-404e-8459-c5ef8d765c8d")
    val UUID_CHARACTERISTIC_CURRENT_TIME: UUID = UUID.fromString("04ca1502-fd57-404e-8459-c5ef8d765c8d") // 0x0018
    val UUID_CHARACTERISTIC_DEVICE_MODE: UUID = UUID.fromString("04ca1503-fd57-404e-8459-c5ef8d765c8d") // 0x001a
    val UUID_CHARACTERISTIC_AUTO_START_STOP: UUID = UUID.fromString("04ca1505-fd57-404e-8459-c5ef8d765c8d") // 0x001f
    val UUID_CHARACTERISTIC_TAG_AUTHENTICATION: UUID = UUID.fromString("04ca1506-fd57-404e-8459-c5ef8d765c8d") // 0x0021
    val UUID_CHARACTERISTIC_UNPAIR: UUID = UUID.fromString("04ca1508-fd57-404e-8459-c5ef8d765c8d") // 0x0027
    val UUID_CHARACTERISTIC_OWNER_AUTHENTICATION: UUID = UUID.fromString("04ca150a-fd57-404e-8459-c5ef8d765c8d") // 0x002b
    val UUID_CHARACTERISTIC_TEMPERATURE_HUMIDITY: UUID = UUID.fromString("04ca1581-fd57-404e-8459-c5ef8d765c8d") // 0x002f
    val UUID_CHARACTERISTIC_BATTERY_LEVEL: UUID = UUID.fromString("04ca1582-fd57-404e-8459-c5ef8d765c8d") // 0x0032

    private const val COMMAND_MANUAL_COLD = "000000030000000000000000"
    private const val COMMAND_MANUAL_HEAT = "000000040000000000000000"
    private const val COMMAND_SMART_COLD = "0400001000000080008000000080800000000000"
    private const val COMMAND_SMART_HEAT = "0400002000000080008000000080800000000000"
    // Smart auto (only available when a Reon Tag is paired): the device switches automatically
    // between smart cool and smart warm based on the tag readings. byte[3]=0x30 selects the mode;
    // byte[13] is the cool threshold and byte[14] the warm threshold, both encoded in 0.5 C steps
    // (value = temperature * 2). e.g. 040000300000008000800000003c140000000000 -> cool 30 C / warm
    // 10 C. The device triggers cooling above the cool threshold and heating below the warm one.
    private const val COMMAND_SMART_AUTO_BASE = "0400003000000080008000000000000000000000"
    // Smart auto start (byte[3]=0x31 running) carries the intensity levels (byte[6]=0x33 cold
    // nibble 3 + heat nibble 3) and leaves the thresholds at 0x8080; the thresholds are configured
    // separately with commandSmartAuto(cool, warm). The stop frame reuses COMMAND_SMART_AUTO_BASE
    // (byte[3]=0x30) with the thresholds set to the disabled placeholder.
    private const val COMMAND_SMART_AUTO_START = "0400003100003309c40fd2000080800000000000"
    private const val SMART_AUTO_THRESHOLD_COOL_OFFSET = 13
    private const val SMART_AUTO_THRESHOLD_WARM_OFFSET = 14
    // Placeholder written to both threshold bytes to disable auto-switching (used by the stop frame).
    private const val SMART_AUTO_THRESHOLD_DISABLED = 0x80.toByte()
    // Thresholds are stored in 0.5 C steps; clamp to the byte range the device accepts.
    const val SMART_AUTO_THRESHOLD_MIN_C = 10.0
    const val SMART_AUTO_THRESHOLD_MAX_C = 30.0
    const val SMART_AUTO_THRESHOLD_COOL_DEFAULT = 25.0
    const val SMART_AUTO_THRESHOLD_WARM_DEFAULT = 20.0
    private const val COMMAND_FEATURE = "020200000000000001"

    // Auto Start/Stop feature. Auto Start/Stop characteristic value: 0180 = enabled, 00 = disabled.
    private const val COMMAND_AUTO_START_STOP_ON = "0180"
    private const val COMMAND_AUTO_START_STOP_OFF = "00"

    // Smart mode intensity levels (0 = lowest, 4 = highest), written to the mode characteristic.
    const val INTENSITY_LEVELS = 5
    private val COMMANDS_INTENSITY_COLD = arrayOf(
        "040000100000010b548000000080800000000000",
        "040000100000020a8c8000000080800000000000",
        "0400001000000309c48000000080800000000000",
        "0400001000000408fc8000000080800000000000",
        "0400001000000507d08000000080800000000000",
    )
    private val COMMANDS_INTENSITY_HEAT = arrayOf(
        "0400002000001080000ed8000080800000000000",
        "0400002000002080000f3c000080800000000000",
        "0400002000003080000fd2000080800000000000",
        "040000200000408000101d000080800000000000",
        "0400002000005080001068000080800000000000",
    )

    // byte[3] of the mode characteristic encodes both dimensions at once.
    const val MODE_BYTE_MANUAL_COOL_ACTIVE = 0x01
    const val MODE_BYTE_MANUAL_HEAT_ACTIVE = 0x02
    const val MODE_BYTE_MANUAL_COLD = 0x03
    const val MODE_BYTE_MANUAL_HEAT = 0x04
    const val MODE_BYTE_SMART_COLD = 0x10
    const val MODE_BYTE_SMART_COOL_ACTIVE = 0x11
    const val MODE_BYTE_SMART_HEAT = 0x20
    const val MODE_BYTE_SMART_HEAT_ACTIVE = 0x21
    // Smart auto (requires a paired Reon Tag): auto-switches between smart cool and smart warm.
    const val MODE_BYTE_SMART_AUTO = 0x30
    const val MODE_BYTE_SMART_AUTO_ACTIVE = 0x31

    // Manual power levels (0 = stop, 1..5 = increasing power), written to the mode characteristic.
    const val MANUAL_LEVELS = 5
    // Owner ID (auth): the 16 identifying bytes persisted per device.
    const val OWNER_ID_LENGTH = 16

    /**
     * Builds the mode command combining both dimensions: [smart] (smart vs manual) and [heat]
     * (heat vs cold).
     */
    fun commandMode(smart: Boolean, heat: Boolean): ByteArray = GB.hexStringToByteArray(
        when {
            smart && heat -> COMMAND_SMART_HEAT
            smart && !heat -> COMMAND_SMART_COLD
            !smart && heat -> COMMAND_MANUAL_HEAT
            else -> COMMAND_MANUAL_COLD
        }
    )

    /**
     * Deterministically derives a 16-byte Owner ID from the device [macAddress] combined with a
     * local, per-installation [localIdentifier]. Mixing in the local identifier ensures two
     * different Gadgetbridge installations do not derive the same Owner ID from the same device,
     * while remaining stable across delete/re-pair on the same installation. Users who want to share
     * a device can still copy the resulting key and paste it into the settings. The result is
     * the first 16 bytes of SHA-256("localIdentifier|MAC").
     */
    fun generateOwnerId(macAddress: String, localIdentifier: String): ByteArray {
        val seed = "$localIdentifier|${macAddress.uppercase(Locale.ROOT)}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return digest.copyOf(OWNER_ID_LENGTH)
    }

    /**
     * Builds the authentication payload written to claim the device: opcode `0x01` followed by the
     * 16-byte [ownerId].
     */
    fun commandAuth(ownerId: ByteArray): ByteArray {
        val payload = ByteArray(1 + OWNER_ID_LENGTH)
        payload[0] = 0x01
        System.arraycopy(ownerId, 0, payload, 1, OWNER_ID_LENGTH)
        return payload
    }

    fun commandFeature(): ByteArray = GB.hexStringToByteArray(COMMAND_FEATURE)

    /**
     * Builds the smart auto command (only meaningful when a Reon Tag is paired). The device switches
     * automatically between smart cool and smart warm using the tag readings: it cools above
     * [coolThresholdCelsius] and warms below [warmThresholdCelsius]. Thresholds are encoded in
     * 0.5 C steps (value = temperature * 2) and clamped to the range the device accepts.
     */
    fun commandSmartAuto(coolThresholdCelsius: Double, warmThresholdCelsius: Double): ByteArray {
        val command = GB.hexStringToByteArray(COMMAND_SMART_AUTO_BASE)
        command[SMART_AUTO_THRESHOLD_COOL_OFFSET] = encodeThreshold(coolThresholdCelsius)
        command[SMART_AUTO_THRESHOLD_WARM_OFFSET] = encodeThreshold(warmThresholdCelsius)
        return command
    }

    private fun encodeThreshold(celsius: Double): Byte {
        val clamped = celsius.coerceIn(SMART_AUTO_THRESHOLD_MIN_C, SMART_AUTO_THRESHOLD_MAX_C)
        return Math.round(clamped * 2).toByte()
    }

    /** Starts smart auto (byte[3]=0x31). Thresholds must be configured with commandSmartAuto(). */
    fun commandSmartAutoStart(): ByteArray = GB.hexStringToByteArray(COMMAND_SMART_AUTO_START)

    /** Stops smart auto (byte[3]=0x30, thresholds reset to the disabled placeholder 0x8080). */
    fun commandSmartAutoStop(): ByteArray {
        val command = GB.hexStringToByteArray(COMMAND_SMART_AUTO_BASE)
        command[SMART_AUTO_THRESHOLD_COOL_OFFSET] = SMART_AUTO_THRESHOLD_DISABLED
        command[SMART_AUTO_THRESHOLD_WARM_OFFSET] = SMART_AUTO_THRESHOLD_DISABLED
        return command
    }

    /** Factory reset: single 0xff byte written to the unpair characteristic. */
    fun commandFactoryReset(): ByteArray = byteArrayOf(0xff.toByte())

    fun commandAutoStartStop(enabled: Boolean): ByteArray = GB.hexStringToByteArray(
        if (enabled) COMMAND_AUTO_START_STOP_ON else COMMAND_AUTO_START_STOP_OFF
    )

    /** Builds the smart mode intensity command for the given direction ([heat]) and [level] (1..5). */
    fun commandIntensity(heat: Boolean, level: Int): ByteArray {
        val index = level.coerceIn(1, INTENSITY_LEVELS) - 1
        val table = if (heat) COMMANDS_INTENSITY_HEAT else COMMANDS_INTENSITY_COLD
        return GB.hexStringToByteArray(table[index])
    }

    /**
     * Builds the command that powers smart mode ON for the given direction ([heat]) and [level]
     * (1..5). It is the intensity command with byte[3]'s low nibble set to the "active" flag
     * (0x11 cold / 0x21 heat).
     */
    fun commandSmartStart(heat: Boolean, level: Int): ByteArray {
        val bytes = commandIntensity(heat, level)
        bytes[3] = (bytes[3].toInt() or 0x01).toByte()
        return bytes
    }

    /**
     * Builds the manual power command for the given direction ([heat]) and [level] (0 = stop,
     * 1..5 = increasing power). byte[3] selects the mode (active or stop) and byte[4] the level.
     */
    fun commandManual(heat: Boolean, level: Int): ByteArray {
        val clamped = level.coerceIn(0, MANUAL_LEVELS)
        val modeByte = when {
            clamped == 0 && heat -> MODE_BYTE_MANUAL_HEAT
            clamped == 0 -> MODE_BYTE_MANUAL_COLD
            heat -> MODE_BYTE_MANUAL_HEAT_ACTIVE
            else -> MODE_BYTE_MANUAL_COOL_ACTIVE
        }
        val levelByte = if (clamped == 0) 0 else clamped - 1
        return byteArrayOf(
            0x00, 0x00, 0x00, modeByte.toByte(), levelByte.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }
    fun commandTimeSync(epochSeconds: Long, timezoneOffsetMinutes: Int): ByteArray = byteArrayOf(
        (epochSeconds and 0xff).toByte(),
        ((epochSeconds shr 8) and 0xff).toByte(),
        ((epochSeconds shr 16) and 0xff).toByte(),
        ((epochSeconds shr 24) and 0xff).toByte(),
        (timezoneOffsetMinutes and 0xff).toByte(),
        ((timezoneOffsetMinutes shr 8) and 0xff).toByte(),
    )

}
