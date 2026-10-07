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
package nodomain.freeyourgadget.gadgetbridge.devices.jabra

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.regex.Pattern

class JabraEvolve65Coordinator : JabraEvolve255Coordinator() {
    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("Jabra EVOLVE 65", Pattern.CASE_INSENSITIVE)
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_jabra_evolve_65
    }

    override fun supportsOSBatteryLevel(device: GBDevice): Boolean {
        return true
    }

    override fun supportsActiveNoiseCancelling(): Boolean {
        return false
    }

    override fun supportsBoomArmFunctions(): Boolean {
        return false
    }

    override fun supportsVoiceAssistant(): Boolean {
        return false
    }

    override fun supportsMultipointPairing(): Boolean {
        return false
    }

    override fun supportsSideTone(): Boolean {
        return false
    }

    override fun supportsDeviceName(): Boolean {
        return false
    }
}
