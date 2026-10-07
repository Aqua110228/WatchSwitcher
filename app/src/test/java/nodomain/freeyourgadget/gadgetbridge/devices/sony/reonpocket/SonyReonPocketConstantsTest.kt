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

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SonyReonPocketConstantsTest {
    private val commands = SonyReonPocketConstants

    private fun assertHex(expected: String, actual: ByteArray) {
        val bytes = expected.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, bytes, actual)
    }

    @Test
    fun smartIntensityAndStartMatchCapturedFrames() {
        // Full pre-refactor frames guard both calibrated values and reserved bytes.
        val cold = listOf(
            "040000100000010b548000000080800000000000",
            "040000100000020a8c8000000080800000000000",
            "0400001000000309c48000000080800000000000",
            "0400001000000408fc8000000080800000000000",
            "0400001000000507d08000000080800000000000",
        )
        val heat = listOf(
            "0400002000001080000ed8000080800000000000",
            "0400002000002080000f3c000080800000000000",
            "0400002000003080000fd2000080800000000000",
            "040000200000408000101d000080800000000000",
            "0400002000005080001068000080800000000000",
        )
        for ((heating, frames) in listOf(false to cold, true to heat)) {
            frames.forEachIndexed { index, frame ->
                assertHex(frame, commands.commandIntensity(heating, index + 1))
                assertHex(frame.replaceRange(6, 8, if (heating) "21" else "11"),
                    commands.commandSmartStart(heating, index + 1))
            }
            for (level in listOf(Int.MIN_VALUE, 0, 1, 5, 6, Int.MAX_VALUE)) {
                assertArrayEquals(commands.commandIntensity(heating, level.coerceIn(1, 5)),
                    commands.commandIntensity(heating, level))
                assertArrayEquals(commands.commandSmartStart(heating, level.coerceIn(1, 5)),
                    commands.commandSmartStart(heating, level))
            }
        }
    }

    @Test
    fun stoppedModesMatchCapturedFrames() {
        assertHex("000000030000000000000000", commands.commandMode(false, false))
        assertHex("000000040000000000000000", commands.commandMode(false, true))
        assertHex("0400001000000080008000000080800000000000", commands.commandMode(true, false))
        assertHex("0400002000000080008000000080800000000000", commands.commandMode(true, true))
    }

    @Test
    fun manualLevelsAndBoundsPreserveFrames() {
        for (heat in listOf(false, true)) {
            assertHex(if (heat) "000000040000000000000000" else "000000030000000000000000",
                commands.commandManual(heat, 0))
            for (level in 1..5) {
                assertHex("0000000${if (heat) 2 else 1}0${level - 1}00000000000000",
                    commands.commandManual(heat, level))
            }
            for (level in listOf(Int.MIN_VALUE, -1, 0, 5, 6, Int.MAX_VALUE)) {
                assertArrayEquals(commands.commandManual(heat, level.coerceIn(0, 5)),
                    commands.commandManual(heat, level))
            }
        }
    }

    @Test
    fun smartAutoPreservesThresholdsAndCapturedStart() {
        assertHex("0400003000000080008000000032280000000000", commands.commandSmartAuto(25.0, 20.0))
        assertHex("040000300000008000800000003c140000000000", commands.commandSmartAuto(100.0, -100.0))
        assertHex("0400003000000080008000000033290000000000", commands.commandSmartAuto(25.25, 20.5))
        assertHex("0400003100003309c40fd2000080800000000000", commands.commandSmartAutoStart())
        assertHex("0400003000000080008000000080800000000000", commands.commandSmartAutoStop())
    }

    @Test
    fun fixedCommandsAndTimeSyncPreserveFrames() {
        assertHex("020200000000000001", commands.commandFeature())
        assertHex("0180", commands.commandAutoStartStop(true))
        assertHex("00", commands.commandAutoStartStop(false))
        assertHex("ff", commands.commandFactoryReset())
        assertHex("785634127800", commands.commandTimeSync(0x12345678, 120))
        assertHex("78563412d4fe", commands.commandTimeSync(0x12345678, -300))
        assertHex("01000102030405060708090a0b0c0d0e0f", commands.commandAuth(ByteArray(16) { it.toByte() }))
    }

    @Test
    fun returnedFramesDoNotShareMutableStorage() {
        val factories = listOf<() -> ByteArray>(
            { commands.commandMode(true, false) },
            { commands.commandMode(true, true) },
            { commands.commandMode(false, false) },
            { commands.commandMode(false, true) },
            { commands.commandIntensity(false, 3) },
            { commands.commandIntensity(true, 3) },
            { commands.commandSmartStart(false, 3) },
            { commands.commandSmartStart(true, 3) },
            { commands.commandSmartAuto(25.0, 20.0) },
            { commands.commandSmartAutoStart() },
            { commands.commandSmartAutoStop() },
            { commands.commandFeature() },
            { commands.commandAutoStartStop(true) },
            { commands.commandAutoStartStop(false) },
            { commands.commandFactoryReset() },
        )
        for (factory in factories) {
            val expected = factory().copyOf()
            factory().fill(0x7f)
            assertArrayEquals(expected, factory())
        }
    }
}
