/*  Copyright (C) 2026 brigon

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
package nodomain.freeyourgadget.gadgetbridge.devices.qnscale

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericWeightSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.qnscale.QnScaleDeviceSupport
import java.util.Collections
import java.util.regex.Pattern

/**
 * A family of Chipsea CS20-based BLE body-composition scales, all advertising as "QN-Scale"
 * regardless of the retail brand printed on the unit (tested against an Arboleaf CS20N). The scale
 * only ever reports raw weight and raw bioimpedance; body composition (fat/water/muscle/bone) is
 * intentionally not derived or stored here, matching this app's own convention for that (see
 * [GenericWeightSampleProvider] / codeberg.org/Freeyourgadget/Gadgetbridge/issues/6393).
 */
class QnScaleCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>?, Property?> {
        return Collections.singletonMap(session.genericWeightSampleDao, GenericWeightSampleDao.Properties.DeviceId)
    }

    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("QN-Scale.*")
    }

    override fun getManufacturer(): String {
        // The BLE module/protocol is Chipsea's; retail units are rebranded by many different vendors.
        return "Chipsea"
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getWeightSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out WeightSample?> {
        return GenericWeightSampleProvider(device, session)
    }

    override fun supportsWeightMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return true
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return QnScaleDeviceSupport::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_qnscale
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_miscale
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.SCALE
    }
}
