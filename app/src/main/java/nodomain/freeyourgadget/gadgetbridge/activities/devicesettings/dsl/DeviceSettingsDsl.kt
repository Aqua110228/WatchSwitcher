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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

@DslMarker
annotation class DeviceSettingMarker

/** Entry point for building a [DeviceSettingsSpec] using a Kotlin DSL. */
fun deviceSettings(block: DeviceSettingsScope.() -> Unit): DeviceSettingsSpec =
    DeviceSettingsSpec(DeviceSettingsScope().apply(block).build())

@DeviceSettingMarker
class DeviceSettingsScope {
    @PublishedApi
    internal val items = mutableListOf<DeviceSetting>()

    fun build(): List<DeviceSetting> = items.toList()

    fun screen(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        xmlSubScreens: List<Int> = emptyList(),
        connectedOnly: Boolean = false,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        enabled: ((Prefs) -> Boolean)? = null,
        block: DeviceSettingsScope.() -> Unit,
    ) {
        items.add(
            ScreenSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                xmlSubScreens = xmlSubScreens,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                enabled = enabled,
                children = DeviceSettingsScope().apply(block).build(),
            )
        )
    }

    fun switchSetting(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @StringRes summaryOn: Int = 0,
        @StringRes summaryOff: Int = 0,
        @DrawableRes icon: Int = 0,
        defaultValue: Boolean = false,
        enabled: Boolean = true,
        dependency: String? = null,
        disableDependentsState: Boolean = false,
        @StringRes confirmationMessage: Int = 0,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            SwitchSetting(
                key = key,
                title = title,
                summary = summary,
                summaryOn = summaryOn,
                summaryOff = summaryOff,
                icon = icon,
                defaultValue = defaultValue,
                enabled = enabled,
                dependency = dependency,
                disableDependentsState = disableDependentsState,
                confirmationMessage = confirmationMessage,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun list(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        @ArrayRes entriesRes: Int,
        @ArrayRes entryValuesRes: Int,
        defaultValue: String = "",
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            ListSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                entriesRes = entriesRes,
                entryValuesRes = entryValuesRes,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun list(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        entries: List<ListEntry>,
        defaultValue: String = "",
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            ListSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                entries = entries,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun list(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        entriesProvider: (Prefs) -> List<ListEntry>,
        defaultValue: String = "",
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            ListSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                entriesProvider = entriesProvider,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun multiSelect(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        entries: List<ListEntry> = emptyList(),
        entriesProvider: ((Prefs) -> List<ListEntry>)? = null,
        defaultValue: Set<String> = emptySet(),
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            MultiSelectSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                entries = entries,
                entriesProvider = entriesProvider,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun sortableList(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        entries: List<ListEntry>,
        defaultValue: List<String> = emptyList(),
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            SortableListSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                entries = entries,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun seekbar(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        min: Int = 0,
        max: Int,
        defaultValue: Int,
        step: Int = 1,
        scale: Double = 1.0,
        showValue: Boolean = true,
        @StringRes valueFormat: Int = 0,
        dependency: String? = null,
        connectedOnly: Boolean = true,
        onSharedPreferenceChanged: ((Int) -> Unit)? = null,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            SeekBarSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                min = min,
                max = max,
                defaultValue = defaultValue,
                step = step,
                scale = scale,
                showValue = showValue,
                valueFormat = valueFormat,
                dependency = dependency,
                connectedOnly = connectedOnly,
                onSharedPreferenceChanged = onSharedPreferenceChanged,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun category(
        key: String,
        @StringRes title: Int = 0,
        titleText: String? = null,
        @DrawableRes icon: Int = 0,
        iconSpaceReserved: Boolean = true,
        connectedOnly: Boolean = false,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        block: DeviceSettingsScope.() -> Unit = {},
    ) {
        items.add(
            CategorySetting(
                key = key,
                title = title,
                titleText = titleText,
                icon = icon,
                iconSpaceReserved = iconSpaceReserved,
                children = DeviceSettingsScope().apply(block).build(),
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun text(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        defaultValue: String = "",
        maxLength: Int? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        dependency: String? = null,
        connectedOnly: Boolean = true,
        enabled: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onSharedPreferenceChanged: ((String) -> Unit)? = null,
        onBindEditText: ((android.widget.EditText) -> Unit)? = null,
        defaultSummary: ((Context) -> String)? = null,
        @StringRes summaryTemplate: Int = 0,
    ) {
        items.add(
            TextSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                defaultValue = defaultValue,
                maxLength = maxLength,
                inputType = inputType,
                dependency = dependency,
                connectedOnly = connectedOnly,
                enabled = enabled,
                visibleWhen = visibleWhen,
                onSharedPreferenceChanged = onSharedPreferenceChanged,
                onBindEditText = onBindEditText,
                defaultSummary = defaultSummary,
                summaryTemplate = summaryTemplate,
            )
        )
    }

    /**
     * A non-interactive, read-only row showing a device-reported value; see [InfoSetting].
     */
    fun info(
        key: String,
        @StringRes title: Int = 0,
        titleText: String? = null,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        iconSpaceReserved: Boolean = true,
        defaultValue: String = "",
        summaryProvider: ((Context, Prefs) -> CharSequence?)? = null,
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
    ) {
        items.add(
            InfoSetting(
                key = key,
                title = title,
                titleText = titleText,
                summary = summary,
                icon = icon,
                iconSpaceReserved = iconSpaceReserved,
                defaultValue = defaultValue,
                summaryProvider = summaryProvider,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
            )
        )
    }

    fun action(
        key: String,
        @StringRes title: Int = 0,
        titleText: String? = null,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        summaryProvider: ((Context, Prefs) -> CharSequence?)? = null,
        dependency: String? = null,
        enabled: Boolean = true,
        @StringRes confirmationMessage: Int = 0,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onClick: ((Context, GBDevice?) -> Boolean)? = null,
    ) {
        items.add(
            ActionSetting(
                key = key,
                title = title,
                titleText = titleText,
                summary = summary,
                icon = icon,
                summaryProvider = summaryProvider,
                dependency = dependency,
                enabled = enabled,
                confirmationMessage = confirmationMessage,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                onClick = onClick,
            )
        )
    }

    fun time(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        defaultValue: String = "",
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onSharedPreferenceChanged: ((String) -> Unit)? = null,
    ) {
        items.add(
            TimeSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                defaultValue = defaultValue,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                onSharedPreferenceChanged = onSharedPreferenceChanged,
            )
        )
    }

    fun date(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        defaultValue: String = "",
        minDate: Long = 0L,
        maxDate: Long = Long.MAX_VALUE,
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onSharedPreferenceChanged: ((String) -> Unit)? = null,
    ) {
        items.add(
            DateSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                defaultValue = defaultValue,
                minDate = minDate,
                maxDate = maxDate,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                onSharedPreferenceChanged = onSharedPreferenceChanged,
            )
        )
    }

    fun externalSettings(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        connectedOnly: Boolean = false,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        activityClass: Class<out AbstractGBActivity>,
    ) {
        action(
            key = key,
            title = title,
            summary = summary,
            icon = icon,
            connectedOnly = connectedOnly,
            visibleWhen = visibleWhen,
        ) { context, device ->
            val intent = Intent(context, activityClass)
            device?.let { intent.putExtra(GBDevice.EXTRA_DEVICE, it) }
            context.startActivity(intent)
            true
        }
    }

    fun filePicker(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @DrawableRes icon: Int = 0,
        mimeTypes: List<String> = listOf("*/*"),
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onPicked: (Context, GBDevice?, List<Uri>) -> Unit,
    ) {
        items.add(
            FilePickerSetting(
                key = key,
                title = title,
                summary = summary,
                icon = icon,
                mimeTypes = mimeTypes,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                onPicked = onPicked,
            )
        )
    }

    fun folderPicker(
        key: String,
        @StringRes title: Int,
        @DrawableRes icon: Int = 0,
        persistUriPermission: Boolean = true,
        dependency: String? = null,
        connectedOnly: Boolean = true,
        visibleWhen: ((Prefs) -> Boolean)? = null,
        onPicked: ((Context, GBDevice?, Uri) -> Unit)? = null,
    ) {
        items.add(
            FolderPickerSetting(
                key = key,
                title = title,
                icon = icon,
                persistUriPermission = persistUriPermission,
                dependency = dependency,
                connectedOnly = connectedOnly,
                visibleWhen = visibleWhen,
                onPicked = onPicked,
            )
        )
    }

    fun xmlScreen(
        screen: DeviceSpecificSettingsScreen,
        vararg subScreens: Int,
        connectedOnly: Boolean = false,
        childConnectedKeys: List<String> = emptyList(),
    ) {
        items.add(
            XmlScreenSetting(
                screen = screen,
                subScreens = subScreens.toList(),
                connectedOnly = connectedOnly,
                childConnectedKeys = childConnectedKeys,
            )
        )
    }
}
