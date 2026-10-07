/*  Copyright (C) 2026 Dominic Monroe

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
package nodomain.freeyourgadget.gadgetbridge.devices.bose

data class NoiseCancellingConfig(
    val maximum: Int,
    val defaultValue: Int,
)

data class BoseDeviceConfig(
    val cnc: NoiseCancellingConfig? = null,
    val anr: NoiseCancellingConfig? = null,
    val standbyTimerDurations: List<Int> = emptyList(),
) {
    companion object {
        @JvmField
        val NC700 = BoseDeviceConfig(
            cnc = NoiseCancellingConfig(
                maximum = 10,
                defaultValue = 10,
            ),
            standbyTimerDurations = listOf(0, 5, 10, 20, 40, 60, 180),
        )

        @JvmField
        val QC35 = BoseDeviceConfig(
            anr = NoiseCancellingConfig(
                maximum = 3,
                defaultValue = 0,
            ),
            standbyTimerDurations = listOf(0, 5, 20, 40, 60, 180),
        )
    }
}
