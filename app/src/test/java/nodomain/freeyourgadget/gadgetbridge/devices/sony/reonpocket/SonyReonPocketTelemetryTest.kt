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

import org.junit.Assert.*
import org.junit.Test

class SonyReonPocketTelemetryTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun decodesCapturedReadAndNotification() {
        for (prefix in listOf("00", "01")) {
            val reading = SonyReonPocketTelemetry.parse(bytes(prefix + "0b2b0b650b22ffff0b1612480b6d000000"))!!
            assertEquals(29.17, reading.panelTemperatureCelsius!!, 0.001)
            assertEquals(1, reading.heatDissipationLevel)
        }
        val reading = SonyReonPocketTelemetry.parse(bytes("010c120a8a0ca1ffff0bea109a0b10000000"))!!
        assertEquals(26.98, reading.panelTemperatureCelsius!!, 0.001)
        assertEquals(2, reading.heatDissipationLevel)
    }

    @Test fun doesNotConfuseSurfaceOrSecondPanelWithDisplayedPanel() {
        // body surface 31.28 C, TEC1 25.67 C, TEC2 27.00 C.
        val reading = SonyReonPocketTelemetry.parse(bytes("010c380a070d3dffff0bd811280a8c000000"))!!
        assertEquals(25.67, reading.panelTemperatureCelsius!!, 0.001)
        assertEquals(2, reading.heatDissipationLevel)
    }

    @Test fun decodesWireCentidegreesWithoutInventingSensorResolution() {
        // TEC2 steps 27.06 -> 27.12 C, but displayed TEC1 stays 25.67 C.
        for (frame in listOf("010c310a070d35ffff0bdb10d80a92000000",
            "010c310a070d32ffff0bde107c0a98000000")) {
            assertEquals(25.67, SonyReonPocketTelemetry.parse(bytes(frame))!!.panelTemperatureCelsius!!, 0.001)
        }
        // Signed big-endian 0x0c35 is exactly 3125 centidegrees, not a /16 fixed-point value.
        val frame = bytes("0100000c350c800000000000000000000000")
        assertEquals(31.25, SonyReonPocketTelemetry.parse(frame)!!.panelTemperatureCelsius!!, 0.001)
    }

    @Test fun matchesDissipationThresholds() {
        for ((raw, level) in listOf(3199 to 1, 3200 to 2, 3599 to 2, 3600 to 3,
            3999 to 3, 4000 to 4, 4299 to 4, 4300 to 5)) {
            val frame = ByteArray(18)
            frame[5] = (raw shr 8).toByte()
            frame[6] = raw.toByte()
            assertEquals(level, SonyReonPocketTelemetry.parse(frame)!!.heatDissipationLevel)
        }
    }

    @Test fun ignoresTruncatedTagAndUnknownFrames() {
        for (size in 0 until 18) assertNull(SonyReonPocketTelemetry.parse(ByteArray(size)))
        for (type in listOf(2, 3, 255)) {
            val frame = ByteArray(18)
            frame[0] = type.toByte()
            assertNull(SonyReonPocketTelemetry.parse(frame))
        }
    }

    @Test fun handlesSignedTemperaturesAndMissingSensorsIndependently() {
        val frame = bytes("010000fe0c8000ffff000000000000000000")
        var reading = SonyReonPocketTelemetry.parse(frame)!!
        assertEquals(-5.0, reading.panelTemperatureCelsius!!, 0.001)
        assertNull(reading.heatDissipationLevel)
        frame[3] = 0xff.toByte()
        frame[4] = 0xff.toByte()
        frame[5] = 0x0c
        frame[6] = 0x80.toByte()
        reading = SonyReonPocketTelemetry.parse(frame)!!
        assertNull(reading.panelTemperatureCelsius)
        assertEquals(2, reading.heatDissipationLevel)
    }
}
