/*  Copyright (C) 2026 José Rebelo

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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureWorldClocks
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.Language
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.MultiSelectSetting
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Adds a language [ListSetting] with key [DeviceSettingsPreferenceConst.PREF_LANGUAGE]. Pass the
 * languages the device supports; if none are given all known [Language] entries are included.
 */
fun DeviceSettingsScope.languages(vararg supported: Language) {
    val languages = if (supported.isEmpty()) Language.entries else supported.toList()
    items.add(
        ListSetting(
            key = DeviceSettingsPreferenceConst.PREF_LANGUAGE,
            title = R.string.pref_title_language,
            icon = R.drawable.ic_language,
            entries = languages.map { ListEntry.Res(it.code, it.label) },
            defaultValue = Language.AUTO.name.lowercase(),
            connectedOnly = true,
        )
    )
}

/**
 * Adds a transliteration [SortableListSetting] with key [DeviceSettingsPreferenceConst.PREF_TRANSLITERATION_LANGUAGES]
 */
fun DeviceSettingsScope.transliteration() {
    val context = GBApplication.getContext()
    val labels = context.resources.getStringArray(R.array.pref_transliteration_languages)
    val values = context.resources.getStringArray(R.array.pref_transliteration_languages_values)
    val entries = labels.zip(values).map { (label, value) -> ListEntry.Text(value, label) }
    val defaultVal = context.resources.getStringArray(R.array.pref_transliteration_languages_default).asList()
    sortableList(
        key = DeviceSettingsPreferenceConst.PREF_TRANSLITERATION_LANGUAGES,
        title = R.string.pref_title_transliteration,
        summary = R.string.pref_summary_transliteration,
        icon = R.drawable.ic_translate,
        entries = entries,
        defaultValue = defaultVal,
        connectedOnly = false,
    )
}

inline fun <reified T> DeviceSettingsScope.enumList(
    key: String,
    @StringRes title: Int,
    @DrawableRes icon: Int = 0,
    defaultValue: T,
    dependency: String? = null,
    connectedOnly: Boolean = true,
    noinline filter: ((T) -> Boolean)? = null,
    noinline visibleWhen: ((Prefs) -> Boolean)? = null,
    noinline onValueChange: ((preference: Preference, oldValue: T, newValue: T) -> Unit)? = null,
) where T : Enum<T>, T : LabeledEntry {
    val all = enumValues<T>()
    val entries = (if (filter != null) all.filter(filter) else all.toList())
        .map { e -> ListEntry.Res(e.name.lowercase(), e.label) }
    items.add(
        ListSetting(
            key = key,
            title = title,
            icon = icon,
            entries = entries,
            defaultValue = defaultValue.name.lowercase(),
            dependency = dependency,
            connectedOnly = connectedOnly,
            visibleWhen = visibleWhen,
            onValueChange = onValueChange?.let { onChange ->
                { preference: Preference, oldValue: String, newValue: String ->
                    val oldEntry = all.firstOrNull { it.name.lowercase() == oldValue } ?: defaultValue
                    val newEntry = all.firstOrNull { it.name.lowercase() == newValue }
                    if (newEntry != null) {
                        onChange(preference, oldEntry, newEntry)
                    }
                }
            },
        )
    )
}

inline fun <reified T> DeviceSettingsScope.multiEnumList(
    key: String,
    @StringRes title: Int,
    @DrawableRes icon: Int = 0,
    defaultValue: Set<T>,
    dependency: String? = null,
    connectedOnly: Boolean = true,
    noinline filter: ((T) -> Boolean)? = null,
    noinline visibleWhen: ((Prefs) -> Boolean)? = null,
) where T : Enum<T>, T : LabeledEntry {
    val all = enumValues<T>()
    val entries = (if (filter != null) all.filter(filter) else all.toList())
        .map { e -> ListEntry.Res(e.name.lowercase(), e.label) }
    items.add(
        MultiSelectSetting(
            key = key,
            title = title,
            icon = icon,
            entries = entries,
            defaultValue = defaultValue.map { it.name.lowercase() }.toSet(),
            dependency = dependency,
            connectedOnly = connectedOnly,
            visibleWhen = visibleWhen,
        )
    )
}

/**
 * A [nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ScreenSetting] for one
 * of the standard [DeviceSpecificSettingsScreen] entries.
 */
fun DeviceSettingsScope.screen(
    screen: DeviceSpecificSettingsScreen,
    @DrawableRes icon: Int,
    xmlSubScreens: List<Int> = emptyList(),
    connectedOnly: Boolean = false,
    visibleWhen: ((Prefs) -> Boolean)? = null,
    block: DeviceSettingsScope.() -> Unit,
) {
    screen(
        key = screen.key,
        title = screen.title,
        icon = icon,
        xmlSubScreens = xmlSubScreens,
        connectedOnly = connectedOnly,
        visibleWhen = visibleWhen,
        block = block,
    )
}

/**
 * The switch that controls whether app notifications are sent to the device.
 */
fun DeviceSettingsScope.sendAppNotifications() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_SEND_APP_NOTIFICATIONS,
        title = R.string.pref_title_send_app_notifications,
        summary = R.string.pref_summary_send_app_notifications,
        icon = R.drawable.ic_notifications,
        defaultValue = true,
        connectedOnly = false,
    )
}

/**
 * The switch that controls whether app notifications are prefixed with the app's name.
 */
fun DeviceSettingsScope.prefixNotificationWithAppName() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_PREFIX_NOTIFICATION_WITH_APP,
        title = R.string.pref_title_prefix_notification_with_app,
        summary = R.string.pref_summary_prefix_notification_with_app,
        icon = R.drawable.ic_notifications,
        defaultValue = true,
        connectedOnly = false,
    )
}

/**
 * The switch that controls whether the device clock is kept in sync with the phone.
 */
fun DeviceSettingsScope.timeSync() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_TIME_SYNC,
        title = R.string.pref_time_sync,
        icon = R.drawable.ic_update,
        defaultValue = true,
        connectedOnly = false,
    )
}

/**
 * The switch that allows a higher MTU.
 */
fun DeviceSettingsScope.highMtu() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_ALLOW_HIGH_MTU,
        title = R.string.pref_title_allow_high_mtu,
        summary = R.string.pref_summary_allow_high_mtu,
        icon = R.drawable.ic_mtu,
        defaultValue = true,
        connectedOnly = false,
    )
}

/**
 * The switch to allow sending the phone's GPS location to the device during a workout. The switch is
 * disabled when the phone has no usable GPS.
 */
fun DeviceSettingsScope.workoutSendGpsToBand() {
    val gpsAvailable = GBLocationService.isGpsSupportedAndEnabled()
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND,
        title = R.string.pref_workout_send_gps_title,
        summary = if (gpsAvailable) R.string.pref_workout_send_gps_summary else R.string.phone_gps_not_available,
        icon = R.drawable.ic_gps_location,
        defaultValue = false,
        enabled = gpsAvailable,
        connectedOnly = false,
    )
}

/**
 * Setting that opens the [ConfigureWorldClocks] activity.
 */
fun DeviceSettingsScope.worldClocks() {
    externalSettings(
        key = DeviceSettingsPreferenceConst.PREF_WORLD_CLOCKS,
        title = R.string.pref_world_clocks_title,
        summary = R.string.pref_world_clocks_summary,
        activityClass = ConfigureWorldClocks::class.java,
    )
}
