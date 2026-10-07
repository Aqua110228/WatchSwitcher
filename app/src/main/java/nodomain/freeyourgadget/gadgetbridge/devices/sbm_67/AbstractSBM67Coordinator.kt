/*  Copyright (C) 2023 Daniele Gobbetti

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.sbm_67

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.bloodPressureActiveUser
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericBloodPressureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericBloodPressureSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.generic_bp.GenericBloodPressureSupport

/**
 * SBM67 devices seem to be sold under multiple brands, with slightly different bluetooth names and bonding behaviors.
 */
abstract class AbstractSBM67Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericBloodPressureSupport::class.java
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BLOOD_PRESSURE_METER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0 // it does not report battery %
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Works just fine if already paired
        return false
    }

    override fun supportsBloodPressureMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun getBloodPressureSampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): GenericBloodPressureSampleProvider? {
        return GenericBloodPressureSampleProvider(device, session)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        bloodPressureActiveUser(device)
    }

    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>?, Property?> {
        val map: MutableMap<AbstractDao<*, *>?, Property?> = HashMap(1)
        map[session.genericBloodPressureSampleDao] = GenericBloodPressureSampleDao.Properties.DeviceId
        return map
    }
}
