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
import java.util.Locale
import java.util.UUID

/**
 * Constants and helpers for the Sony Reon Tag (model RNPT-1), a small BLE temperature reader
 * connected to Reon Pocket, that passes the temperature to automatically regulate heat/cool power.
 *
 * Data is processed from BLE advertisement and contains an encrypted payload.
 */
object SonyReonTagConstants {
    /** Advertised local name of the Reon Tag. */
    const val ADVERTISED_NAME = "RNPT-1"

    /** Marketing model identifier. */
    const val MODEL = "RNPT-1"

    /**
     * The tag's primary GATT service UUID.
     * On air the 128-bit UUID is advertised little-endian:
     * cc cf 1e 48 e1 b9 08 b3 30 43 83 e7 01 15 67 16
     * Characteristics follow the `166715XX` pattern,
     * mirroring the Reon Pocket Pro's `04ca15XX` scheme.
     */
    val SERVICE_UUID: UUID = UUID.fromString("16671501-e783-4330-b308-b9e1481ecfcc")

    val DECRYPTION_KEY: ByteArray = byteArrayOf(
        0xD2.toByte(), 0x23.toByte(), 0xEC.toByte(), 0x09.toByte(),
        0x13.toByte(), 0x84.toByte(), 0xF9.toByte(), 0x96.toByte(),
        0x31.toByte(), 0x45.toByte(), 0xFC.toByte(), 0xF2.toByte(),
        0xCF.toByte(), 0xFA.toByte(), 0x34.toByte(), 0x41.toByte()
    )

    // --- Tag registration (written to the Reon Pocket Pro, not the tag itself) ---
    private val REGISTER_TAG_OPCODE = byteArrayOf(0x02, 0x01)
    private val UNREGISTER_TAG_OPCODE = byteArrayOf(0x02, 0x02)
    // Bluetooth address type appended after the address (0x01 = random, matching the tag's RPA).
    private const val ADDRESS_TYPE_RANDOM: Byte = 0x01

    // --- Tag telemetry (Reon Pocket telemetry characteristic 0x002f, subframe 0x02) ---
    // The existing subframe 0x01 carries the Pocket's own sensors; subframe 0x02 carries the tag.
    // Layout: 02 04 08 <slot> <tempC*100 i16 BE> <humidity*100 i16 BE> <u16> <u16> <batt> <..>
    // e.g. 02 04 08 01 0a d2 11 df 00 aa 00 5a 90 64 ... -> 27.70 C, 45.75 %, battery 0x64=100 %.
    const val TELEMETRY_SUBFRAME_TAG = 0x02
    private const val TELEMETRY_TAG_SLOT_OFFSET = 3
    private const val TELEMETRY_TAG_TEMPERATURE_OFFSET = 4
    private const val TELEMETRY_TAG_HUMIDITY_OFFSET = 6
    // Third int16 (offset 8) varies slightly with temperature/humidity; meaning not yet confirmed.
    private const val TELEMETRY_TAG_AUX_OFFSET = 8
    // Last observed byte pair before padding: high byte constant (0x90), low byte 0x64 = 100 %.
    private const val TELEMETRY_TAG_BATTERY_OFFSET = 13
    private const val INVALID_READING = 0xffff
    private const val CENTI = 100.0f

    data class TagReading(
        val slot: Int,
        val temperatureCelsius: Float?,
        val humidityPercent: Float?,
        val batteryPercent: Int?,
        val auxRaw: Int?,
    )

    fun isReonTag(deviceName: String?): Boolean =
        deviceName?.trim()?.equals(ADVERTISED_NAME, ignoreCase = true) == true

    /** Whether the telemetry [value] (from Pocket handle 0x002f) is a tag subframe (0x02). */
    fun isTagTelemetry(value: ByteArray?): Boolean =
        value != null && value.isNotEmpty() && (value[0].toInt() and 0xff) == TELEMETRY_SUBFRAME_TAG

    /**
     * Decodes a tag telemetry subframe (0x02) into a [TagReading]. Temperature and humidity are
     * big-endian int16 in centi-units (0xFFFF meaning "no reading"); battery is a raw percentage.
     * Returns `null` if [value] is not a tag frame or is too short.
     */
    fun parseTagTelemetry(value: ByteArray?): TagReading? {
        if (!isTagTelemetry(value) || value == null) {
            return null
        }
        val slot = readByte(value, TELEMETRY_TAG_SLOT_OFFSET) ?: 0
        return TagReading(
            slot = slot,
            temperatureCelsius = readCenti(value, TELEMETRY_TAG_TEMPERATURE_OFFSET),
            humidityPercent = readCenti(value, TELEMETRY_TAG_HUMIDITY_OFFSET),
            batteryPercent = readByte(value, TELEMETRY_TAG_BATTERY_OFFSET),
            auxRaw = readU16(value, TELEMETRY_TAG_AUX_OFFSET),
        )
    }

    private fun readByte(value: ByteArray, offset: Int): Int? =
        if (offset < value.size) value[offset].toInt() and 0xff else null

    private fun readU16(value: ByteArray, offset: Int): Int? =
        if (offset + 1 < value.size) {
            ((value[offset].toInt() and 0xff) shl 8) or (value[offset + 1].toInt() and 0xff)
        } else {
            null
        }

    private fun readCenti(value: ByteArray, offset: Int): Float? {
        val raw = readU16(value, offset) ?: return null
        return if (raw == INVALID_READING) null else raw / CENTI
    }

    fun commandRegisterTag(tagAddress: String): ByteArray {
        val addressLittleEndian = parseAddress(tagAddress).reversedArray()
        return REGISTER_TAG_OPCODE + addressLittleEndian + ADDRESS_TYPE_RANDOM
    }

    fun commandUnregisterTag(tagAddress: String): ByteArray {
        val addressLittleEndian = parseAddress(tagAddress).reversedArray()
        return UNREGISTER_TAG_OPCODE + addressLittleEndian + ADDRESS_TYPE_RANDOM
    }

    /**
     * Parses a colon or dash-separated Bluetooth address (e.g. `EA:65:60:11:22:33`)
     * into 6-byte big-endian (display order).
     */
    fun parseAddress(address: String): ByteArray {
        val hex = address.replace(":", "").replace("-", "")
        require(hex.length == 12) { "Invalid Bluetooth address: $address" }
        return GB.hexStringToByteArray(hex.uppercase(Locale.ROOT))
    }

    // --- Advertising payload (Manufacturer Specific Data) ---
    // Payload is obfuscated with the fixed repeating-key XOR (see [DECRYPTION_KEY]).
    // Decrypting it exposes the tag's real identity (the announced BD address
    // may be an ephemeral random address, so the decrypted BD address is authoritative).
    // For the decrypted buffer `dec`:
    //   dec[12..15] serial, BCD, reversed -> the printed serial id (e.g. 01 17 12 34 -> 1171234)
    //   dec[16..21] BD address, reversed  -> EA:65:60:11:22:33
    private const val ADV_SERIAL_OFFSET = 12
    private const val ADV_SERIAL_LENGTH = 4
    private const val ADV_BD_ADDRESS_OFFSET = 16
    private const val ADV_BD_ADDRESS_LENGTH = 6
    private const val ADV_MIN_LENGTH = ADV_BD_ADDRESS_OFFSET + ADV_BD_ADDRESS_LENGTH

    data class TagIdentity(
        val serial: String,
        val bdAddress: String,
    )

    /**
     * Deobfuscates an advertising payload by XORing it with the fixed [DECRYPTION_KEY]. The scheme is
     * symmetric, so the same call also re-obfuscates a plaintext buffer.
     */
    fun decryptAdvertisement(raw: ByteArray): ByteArray {
        val out = ByteArray(raw.size)
        for (i in raw.indices) {
            out[i] = (raw[i].toInt() xor DECRYPTION_KEY[i % DECRYPTION_KEY.size].toInt()).toByte()
        }
        return out
    }

    /**
     * Decrypts [raw] and extracts the tag's printed serial and real BD address. Returns `null` if [raw]
     * is too short. The serial is stored as BCD: each byte's two hex nibbles are literal decimal
     * digits (e.g. `01 17 12 34` -> `01171234` -> `1171234`). The BD address should be trusted over
     * the advertised (possibly ephemeral) address used during the scan.
     */
    fun parseAdvertisement(raw: ByteArray?): TagIdentity? {
        if (raw == null || raw.size < ADV_MIN_LENGTH) {
            return null
        }
        val plain = decryptAdvertisement(raw)
        val serialBytes = plain.copyOfRange(ADV_SERIAL_OFFSET, ADV_SERIAL_OFFSET + ADV_SERIAL_LENGTH).reversedArray()
        val serialBcd = serialBytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }
        val serial = serialBcd.trimStart('0').ifEmpty { "0" }
        val bdBytes = plain.copyOfRange(ADV_BD_ADDRESS_OFFSET, ADV_BD_ADDRESS_OFFSET + ADV_BD_ADDRESS_LENGTH).reversedArray()
        val bdAddress = bdBytes.joinToString(":") { String.format(Locale.ROOT, "%02X", it) }
        return TagIdentity(serial, bdAddress)
    }
}
