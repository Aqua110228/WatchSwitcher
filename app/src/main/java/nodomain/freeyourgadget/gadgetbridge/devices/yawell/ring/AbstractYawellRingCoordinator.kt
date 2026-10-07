/*  Copyright (C) 2024-2026 Arjan Schrijver

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
package nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.heartrateMeasurementInterval
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability.MeasurementInterval
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.ColmiHrvValueSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.ColmiSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.ColmiStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.ColmiTemperatureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.ComputedHrvSummarySampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.samples.ColmiActivitySampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiActivitySampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiHeartRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiHrvValueSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiSleepSessionSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiSleepStageSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiSpo2SampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.ColmiStressSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.model.StressSample
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.yawell.ring.YawellRingDeviceSupport

abstract class AbstractYawellRingCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>?, Property?> {
        return object : HashMap<AbstractDao<*, *>?, Property?>() {
            init {
                put(session.colmiActivitySampleDao, ColmiActivitySampleDao.Properties.DeviceId)
                put(
                    session.colmiHeartRateSampleDao,
                    ColmiHeartRateSampleDao.Properties.DeviceId
                )
                put(session.colmiSpo2SampleDao, ColmiSpo2SampleDao.Properties.DeviceId)
                put(session.colmiStressSampleDao, ColmiStressSampleDao.Properties.DeviceId)
                put(
                    session.colmiSleepSessionSampleDao,
                    ColmiSleepSessionSampleDao.Properties.DeviceId
                )
                put(
                    session.colmiSleepStageSampleDao,
                    ColmiSleepStageSampleDao.Properties.DeviceId
                )
                put(session.colmiHrvValueSampleDao, ColmiHrvValueSampleDao.Properties.DeviceId)
            }
        }
    }

    override fun getManufacturer(): String? {
        return "Colmi"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return YawellRingDeviceSupport::class.java
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_smartring
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun supportsPowerOff(device: GBDevice): Boolean {
        return true
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean {
        return true
    }

    override fun supportsDataFetching(device: GBDevice): Boolean {
        return true
    }

    override fun supportsRealtimeData(device: GBDevice): Boolean {
        return true
    }

    override fun supportsStressMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsSpo2(device: GBDevice): Boolean {
        return true
    }

    override fun supportsHeartRateStats(device: GBDevice): Boolean {
        return true
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsRemSleep(device: GBDevice): Boolean {
        return true
    }

    override fun supportsAwakeSleep(device: GBDevice): Boolean {
        return true
    }

    override fun supportsHrvMeasurement(device: GBDevice): Boolean {
        return true
    }

    open fun hasDisplay(): Boolean {
        return false
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out ColmiActivitySample?> {
        return ColmiActivitySampleProvider(device, session)
    }

    override fun getSpo2SampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out Spo2Sample?>? {
        return ColmiSpo2SampleProvider(device, session)
    }

    override fun getStressSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out StressSample?>? {
        return ColmiStressSampleProvider(device, session)
    }

    override fun getHrvSummarySampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out HrvSummarySample?>? {
        return ComputedHrvSummarySampleProvider(
            getHrvValueSampleProvider(device, session),
            device,
            session
        )
    }

    override fun getHrvValueSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out HrvValueSample?>? {
        return ColmiHrvValueSampleProvider(device, session)
    }

    override fun getTemperatureSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out TemperatureSample?>? {
        return ColmiTemperatureSampleProvider(device, session)
    }

    override fun getHeartRateMeasurementIntervals(): MutableList<MeasurementInterval?> {
        return mutableListOf(
            MeasurementInterval.OFF,
            MeasurementInterval.MINUTES_5,
            MeasurementInterval.MINUTES_10,
            MeasurementInterval.MINUTES_15,
            MeasurementInterval.MINUTES_30,
            MeasurementInterval.MINUTES_45,
            MeasurementInterval.HOUR_1
        )
    }

    override fun getStressRanges(): IntArray? {
        // 1-29 = relaxed
        // 30-59 = normal
        // 60-79 = medium
        // 80-99 = high
        return intArrayOf(1, 30, 60, 80)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.HEALTH.key,
            title = R.string.pref_header_health,
            icon = R.drawable.ic_health,
        ) {
            heartrateMeasurementInterval(
                heartRateMeasurementIntervals
            )
            switchSetting(
                key = "heartrate_stress_monitoring",
                title = R.string.prefs_stress_monitoring_title,
                summary = R.string.prefs_stress_monitoring_description,
                icon = R.drawable.ic_mood_bad,
                defaultValue = false,
            )
            switchSetting(
                key = "spo2_all_day_monitoring_enabled",
                title = R.string.prefs_spo2_monitoring_title,
                summary = R.string.prefs_spo2_monitoring_description,
                icon = R.drawable.ic_spo2,
                defaultValue = false,
            )
            switchSetting(
                key = "hrv_all_day_monitoring_enabled",
                title = R.string.prefs_hrv_monitoring_title,
                summary = R.string.prefs_hrv_monitoring_description,
                icon = R.drawable.ic_show_chart,
                defaultValue = false,
            )
            switchSetting(
                key = "continuous_skin_temperature_measurement",
                title = R.string.pref_continuous_skin_temperature_measurement_title,
                icon = R.drawable.ic_temperature,
                defaultValue = false,
                visibleWhen = { supportsContinuousTemperature(device) },
            )
        }
        screen(
            key = DeviceSpecificSettingsScreen.DISPLAY.key,
            title = R.string.pref_header_display,
            icon = R.drawable.ic_device_display,
            visibleWhen = { hasDisplay() },
        ) {
            switchSetting(
                key = "display_enabled",
                title = R.string.pref_display_enabled_title,
                summary = R.string.pref_display_enabled_summary,
                icon = R.drawable.ic_power_settings_new,
                defaultValue = false,
            )
            list(
                key = "wearlocation",
                title = R.string.prefs_wearside,
                dependency = "display_enabled",
                defaultValue = "left",
                icon = R.drawable.ic_switch_left,
                entriesRes = R.array.wearside,
                entryValuesRes = R.array.wearmode_values,
            )
            seekbar(
                key = "screen_brightness",
                title = R.string.pref_screen_brightness,
                icon = R.drawable.ic_wb_sunny,
                dependency = "display_enabled",
                defaultValue = 2,
                max = 4,
            )
            switchSetting(
                key = "display_all_day",
                title = R.string.pref_display_enabled_all_day_title,
                summary = R.string.pref_display_enabled_all_day_summary,
                icon = R.drawable.ic_timer,
                dependency = "display_enabled",
                disableDependentsState = true,
                defaultValue = true,
            )
            time(
                key = "display_on_start",
                title = R.string.mi2_prefs_do_not_disturb_start,
                icon = R.drawable.ic_start,
                dependency = "display_all_day",
                defaultValue = "07:00",
            )
            time(
                key = "display_on_end",
                title = R.string.mi2_prefs_do_not_disturb_end,
                icon = R.drawable.ic_keyboard_tab,
                dependency = "display_all_day",
                defaultValue = "22:00",
            )
        }
    }

    override fun getLiveActivityFragmentPulseInterval(): Int {
        return 2000
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind? {
        return DeviceCoordinator.DeviceKind.RING
    }
}
