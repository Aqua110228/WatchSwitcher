package nodomain.freeyourgadget.gadgetbridge.devices.beurer

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
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
import java.util.regex.Pattern

class BeurerBm69Coordinator : AbstractBLEDeviceCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^BM69")
    }

    override fun getManufacturer(): String {
        return "Beurer"
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_beurer_bm_69
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericBloodPressureSupport::class.java
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BLOOD_PRESSURE_METER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0 // unconfirmed
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
        session: DaoSession
    ): GenericBloodPressureSampleProvider {
        return GenericBloodPressureSampleProvider(device, session)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        bloodPressureActiveUser(device)
    }

    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>, Property> {
        val map: MutableMap<AbstractDao<*, *>, Property> = HashMap(1)
        map[session.genericBloodPressureSampleDao] = GenericBloodPressureSampleDao.Properties.DeviceId
        return map
    }
}
