package nodomain.freeyourgadget.gadgetbridge.devices.viatom

import android.text.InputType
import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.DeviceKind
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.F8WeightSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericImpedanceSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.viatom.F8Support
import java.util.regex.Pattern

class F8ScaleCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>?, Property?> {
        return object : HashMap<AbstractDao<*, *>?, Property?>() {
            init {
                put(session.f8WeightSampleDao, F8WeightSampleDao.Properties.DeviceId)
                put(
                    session.genericImpedanceSampleDao,
                    GenericImpedanceSampleDao.Properties.DeviceId
                )
            }
        }
    }

    override fun getWeightSampleProvider(
        device: GBDevice, session: DaoSession
    ): TimeSampleProvider<out WeightSample?> {
        return F8WeightSampleProvider(device, session)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = "pref_screen_users",
            title = R.string.f8_prefs_users,
            icon = R.drawable.ic_badge
        ) {
            for (i in 1..numUsers()) {
                //empty inactive row. Just to show a seprator
                action(
                    key = "user${i}"
                )
                text(
                    key = "u${i}_scale_user_alias",
                    title = R.string.miband_prefs_alias,
                    icon = R.drawable.ic_badge,
                    maxLength = 17,
                    connectedOnly = false,
                )
                date(
                    key = "u${i}_scale_user_date_of_birth",
                    title = R.string.activity_prefs_date_birth,
                    icon = R.drawable.ic_calendar_month,
                    connectedOnly = false
                )
                list(
                    key = "u${i}_scale_user_gender",
                    title = R.string.activity_prefs_gender,
                    icon = R.drawable.ic_wc,
                    entriesRes = R.array.gender,
                    entryValuesRes = R.array.gender_values,
                    connectedOnly = false,
                )
                text(
                    key = "u${i}_scale_user_height_cm",
                    title = R.string.activity_prefs_height_cm,
                    icon = R.drawable.ic_height,
                    maxLength = 3,
                    connectedOnly = false,
                    inputType = InputType.TYPE_CLASS_NUMBER
                )
                text(
                    key = "u${i}_scale_user_weight_kg",
                    title = R.string.activity_prefs_weight_kg,
                    icon = R.drawable.ic_weight,
                    maxLength = 3,
                    connectedOnly = false,
                    inputType = InputType.TYPE_CLASS_NUMBER
                )
                text(
                    key = "u${i}_scale_user_goal_weight_kg",
                    title = R.string.activity_prefs_target_weight_kg,
                    icon = R.drawable.ic_monitor_weight,
                    maxLength = 3,
                    connectedOnly = false,
                    inputType = InputType.TYPE_CLASS_NUMBER
                )
            }
        }
        list(
            key = "active_user",
            title = R.string.f8_prefs_active_user_title,
            connectedOnly = false,
            entriesProvider = { prefs ->
                (1..numUsers()).map { i ->
                    val alias = prefs.getString("u${i}_scale_user_alias", null)
                    ListEntry.Text(
                        value = i.toString(),
                        label = if (alias.isNullOrBlank()) "User $i" else alias,
                    )
                }
            },
            defaultValue = "1",
        )
    }

    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("F8")
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getManufacturer(): String {
        return "Viatom"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return F8Support::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_f8
    }

    override fun getDeviceKind(device: GBDevice): DeviceKind {
        return DeviceKind.SCALE
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_miscale
    }

    override fun supportsWeightMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return true
    }

    //helpers for the DSL

    fun numUsers(): Int {
        return 2 // actual limit is 24: https://www.viatomtech.com/smart-body-composition-scale-p1
    }
}
