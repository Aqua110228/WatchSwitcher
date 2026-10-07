/*  Copyright (C) 2026 José Rebelo, Arjan Schrijver

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListSetting
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability
import nodomain.freeyourgadget.gadgetbridge.devices.GenericBloodPressureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.bloodpressure.BloodPressureProfile
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectPermissionManager
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectUtils

/**
 * Adds a heart rate interval [ListSetting] with key [DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL].
 * Pass the intervals the device supports.
 */
fun DeviceSettingsScope.heartrateMeasurementInterval(supported: MutableList<HeartRateCapability.MeasurementInterval?>) {
    val intervals = if (supported.isEmpty()) listOf(HeartRateCapability.MeasurementInterval.OFF) else supported
    items.add(
        ListSetting(
            key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL,
            title = R.string.prefs_title_heartrate_measurement_interval,
            icon = R.drawable.ic_heartrate,
            entries = intervals.map { ListEntry.Res(it!!.intervalSeconds.toString(), it.label) },
            defaultValue = "0",
            connectedOnly = true,
        )
    )
}

/**
 * Adds a [ListSetting] with key [DeviceSettingsPreferenceConst.PREF_BLOOD_PRESSURE_ACTIVE_USER].
 * The entries are the distinct user indexes in the blood pressure samples of [device].
 */
fun DeviceSettingsScope.bloodPressureActiveUser(device: GBDevice) {
    items.add(
        ListSetting(
            key = DeviceSettingsPreferenceConst.PREF_BLOOD_PRESSURE_ACTIVE_USER,
            title = R.string.f8_prefs_active_user_title,
            icon = R.drawable.ic_person,
            entriesProvider = {
                val userIndexes = GBApplication.acquireDbReadOnly().use { db ->
                    GenericBloodPressureSampleProvider(device, db.daoSession).userIndexes
                }
                val context = GBApplication.getContext()
                listOf<ListEntry>(ListEntry.Res("-1", R.string.blood_pressure_all_users)) +
                    userIndexes.map {
                        if (it == BloodPressureProfile.USER_ID_UNKNOWN) {
                            ListEntry.Res(it.toString(), R.string.blood_pressure_unknown_user)
                        } else {
                            ListEntry.Text(
                                it.toString(),
                                context.getString(R.string.blood_pressure_user_index, it)
                            )
                        }
                    }
            },
            defaultValue = "-1",
            connectedOnly = false,
            onValueChange = { _, oldValue, newValue ->
                if (oldValue != newValue) {
                    HealthConnectUtils.resetSyncState(
                        device,
                        HealthConnectPermissionManager.HealthConnectDataType.BLOOD_PRESSURE
                    )
                }
            },
        )
    )
}
