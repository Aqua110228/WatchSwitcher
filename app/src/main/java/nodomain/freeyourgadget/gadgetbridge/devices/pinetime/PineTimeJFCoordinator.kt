/*  Copyright (C) 2020-2025 Andreas Shimokawa, Damien Gaignon, Daniel Dakhno,
    Davis Mosenkovs, ITCactus, José Rebelo, Patric Gruber, Petr Vaněk, Taavi
    Eomäe, uli, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.devices.pinetime

import android.content.Context
import android.net.Uri
import android.os.Bundle
import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.prefixNotificationWithAppName
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.transliteration
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.worldClocks
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.PineTimeActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.PineTimeActivitySampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.pinetime.PineTimeJFSupport
import java.util.regex.Pattern

class PineTimeJFCoordinator : AbstractBLEDeviceCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("Pinetime-JF.*|InfiniTime.*")
    }

    override fun findInstallHandler(uri: Uri, options: Bundle, context: Context): InstallHandler? {
        val handler = PineTimeInstallHandler(uri, context)
        return if (handler.isValid) handler else null
    }

    override fun supportsFlashing(device: GBDevice): Boolean {
        return true
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean {
        return true
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out PineTimeActivitySample?> {
        return PineTimeActivitySampleProvider(device, session)
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean {
        return false
    }

    override fun getManufacturer(): String {
        return "Pine64"
    }

    override fun supportsWeather(device: GBDevice): Boolean {
        return true
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun supportsMusicInfo(device: GBDevice): Boolean {
        return true
    }

    override fun getWorldClocksSlotCount(): Int {
        return 4
    }

    override fun getWorldClocksLabelLength(): Int {
        return 8
    }

    override fun supportsNavigation(device: GBDevice): Boolean {
        return true
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return PineTimeJFSupport::class.java
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        transliteration()
        worldClocks()
        prefixNotificationWithAppName()
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_pinetime_jf
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_pinetime
    }

    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>?, Property?> {
        return object : HashMap<AbstractDao<*, *>?, Property?>() {
            init {
                put(session.pineTimeActivitySampleDao, PineTimeActivitySampleDao.Properties.DeviceId)
            }
        }
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.WATCH
    }
}
