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
@file:Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")

package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Sealed hierarchy describing a single device setting node. Nodes are purely declarative -
 * they carry no Android context and can be built by a coordinator and re-evaluated cheaply.
 *
 * Common semantics:
 *  - [key]          - SharedPreferences key (or navigation screen key for screen nodes).
 *  - [visibleWhen]  - predicate evaluated against current SharedPreferences; null means always
 *    visible. Re-evaluated after any preference in the screen changes.
 *  - [connectedOnly] - preference is disabled when the device is not connected.
 */
sealed class DeviceSetting {
    abstract val key: String
    abstract val visibleWhen: ((Prefs) -> Boolean)?
    abstract val connectedOnly: Boolean
}

/**
 * A [DeviceSetting] that contains child settings. Both [ScreenSetting] and [CategorySetting]
 * are groups; tree-walking code can match on this type to recurse without caring which kind.
 */
sealed class GroupSetting : DeviceSetting() {
    abstract val children: List<DeviceSetting>
}

/**
 * A navigable sub-screen whose children are rendered programmatically when entered.
 * In the root preference list this appears as a single tappable row (title + icon).
 */
data class ScreenSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    /**
     * Legacy XML screens, appended after [children] when this screen is opened. Should only be used
     * by screens whose key is one of [DeviceSpecificSettingsScreen].
     */
    val xmlSubScreens: List<Int> = emptyList(),
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    val enabled: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    override val children: List<DeviceSetting> = emptyList(),
) : GroupSetting()

/**
 * A switch boolean setting, equivalent to SwitchPreferenceCompat.
 * [summaryOn] and [summaryOff] show different text depending on the checked state; [summary] shows
 * static text regardless. If none are set, no summary is displayed.
 */
data class SwitchSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @StringRes val summaryOn: Int = 0,
    @StringRes val summaryOff: Int = 0,
    @DrawableRes val icon: Int = 0,
    val defaultValue: Boolean = false,
    val enabled: Boolean = true,
    val dependency: String? = null,
    /** Mirrors androidx's `app:disableDependentsState` */
    val disableDependentsState: Boolean = false,
    /** When non-zero, changing this setting shows a confirmation dialog with this message before the new value is applied. */
    @StringRes val confirmationMessage: Int = 0,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
) : DeviceSetting()

/**
 * A list setting, equivalent to ListPreference. Exactly one entry source must be provided:
 *  - [entriesProvider]: evaluated from SharedPreferences at render time and on every refresh -
 *    use this for presets fetched from the device at connection.
 *  - [entries]: static list built at DSL construction time (e.g. from a [LabeledEntry] enum).
 *  - [entriesRes] + [entryValuesRes]: legacy resource arrays, for devices not yet migrated to
 *    programmatic entries.
 *
 * If [summary] is set it overrides the default behaviour of showing the selected entry as summary.
 *
 * [onValueChange] receives the old and the new value before the new value is stored.
 */
data class ListSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    @ArrayRes val entriesRes: Int = 0,
    @ArrayRes val entryValuesRes: Int = 0,
    val entries: List<ListEntry> = emptyList(),
    val entriesProvider: ((Prefs) -> List<ListEntry>)? = null,
    val defaultValue: String = "",
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onValueChange: ((preference: Preference, oldValue: String, newValue: String) -> Unit)? = null,
) : DeviceSetting()

/** A seek bar setting, equivalent to SeekBarPreference. */
data class SeekBarSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val min: Int = 0,
    val max: Int,
    val defaultValue: Int,
    val step: Int = 1,
    val scale: Double = 1.0,
    val showValue: Boolean = true,
    @StringRes val valueFormat: Int = 0,
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    val onSharedPreferenceChanged: ((Int) -> Unit)? = null,
    override val connectedOnly: Boolean = true,
) : DeviceSetting()

/** A preference category header, equivalent to PreferenceCategory. Children are rendered inside the group. */
data class CategorySetting(
    override val key: String,
    @StringRes val title: Int,
    /** Plain title, for a header built at runtime. Overrides [title] when set. */
    val titleText: String? = null,
    @DrawableRes val icon: Int = 0,
    val iconSpaceReserved: Boolean = true,
    override val children: List<DeviceSetting> = emptyList(),
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = false,
) : GroupSetting()

/**
 * A free text setting, equivalent to EditTextPreference.
 * If [summary] is set it overrides the default behaviour of showing the current value as summary.
 * Set [enabled] to false to make the field read-only (displayed but not editable).
 */
data class TextSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val defaultValue: String = "",
    val maxLength: Int? = null,
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val enabled: Boolean = true,
    val onSharedPreferenceChanged: ((String) -> Unit)? = null,
    val onBindEditText: ((EditText) -> Unit)? = null,
    /** When non-null, shown as the summary when no value is set. */
    val defaultSummary: ((Context) -> String)? = null,
    /** When non-zero, wraps the current value in this format string for the summary. */
    @StringRes val summaryTemplate: Int = 0,
) : DeviceSetting()

/**
 * A non-interactive, read-only row that just displays a SharedPreferences string value as its
 * summary -- for device-reported values that are never meant to be edited.
 * Backed by a plain `Preference` (not EditTextPreference): no dialog, never selectable.
 */
data class InfoSetting(
    override val key: String,
    @StringRes val title: Int,
    /** Plain title, for a row built at runtime. Overrides [title] when set. */
    val titleText: String? = null,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val iconSpaceReserved: Boolean = true,
    val defaultValue: String = "",
    /**
     * Computes the summary. Overrides [summary]. Re-evaluated on every refresh,
     * so it can show a derived value.
     */
    val summaryProvider: ((Context, Prefs) -> CharSequence?)? = null,
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
) : DeviceSetting()

/**
 * A non-persistent action preference. [onClick] receives the current context and device, so it can
 * launch activities or invoke device-specific operations.
 */
data class ActionSetting(
    override val key: String,
    @StringRes val title: Int = 0,
    /** Plain title, for a row built at runtime. Overrides [title] when set. */
    val titleText: String? = null,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    /**
     * Computes the summary. Overrides [summary]. Re-evaluated on every refresh,
     * so it can show a derived value.
     */
    val summaryProvider: ((Context, Prefs) -> CharSequence?)? = null,
    val dependency: String? = null,
    val enabled: Boolean = true,
    /** When non-zero, tapping this action shows a confirmation dialog with this message before [onClick] runs. */
    @StringRes val confirmationMessage: Int = 0,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onClick: ((Context, GBDevice?) -> Boolean)? = null,
) : DeviceSetting()

/**
 * A multi-select list setting, equivalent to MultiSelectListPreference. Entry sources mirror
 * [ListSetting]: exactly one of [entriesProvider] or [entries] should be provided.
 */
data class MultiSelectSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val entries: List<ListEntry> = emptyList(),
    val entriesProvider: ((Prefs) -> List<ListEntry>)? = null,
    val defaultValue: Set<String> = emptySet(),
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
) : DeviceSetting()

/**
 * An ordered multi-select list setting, equivalent to DragSortListPreference. The stored value
 * is the selected entry values in the chosen order, comma separated.
 */
data class SortableListSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val entries: List<ListEntry>,
    val defaultValue: List<String> = emptyList(),
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
) : DeviceSetting()

/**
 * A time setting, backed by a custom DialogPreference (e.g. XTimePreference) that persists the
 * value as a "HH:mm" string.
 */
data class TimeSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val defaultValue: String = "",
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onSharedPreferenceChanged: ((String) -> Unit)? = null,
) : DeviceSetting()

/**
 * A date setting, backed by a custom DialogPreference (e.g. XDatePreference) that persists the
 * value as a "yyyy-MM-dd" string.
 */
data class DateSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    val defaultValue: String = "",
    val minDate: Long = 0L,
    val maxDate: Long = Long.MAX_VALUE,
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onSharedPreferenceChanged: ((String) -> Unit)? = null,
) : DeviceSetting()

/**
 * A preference that opens the system document picker, allowing several files to be selected.
 * The picked files are passed to [onPicked]. Nothing is stored under [key].
 */
data class FilePickerSetting(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int = 0,
    @DrawableRes val icon: Int = 0,
    /** MIME types the picker accepts. */
    val mimeTypes: List<String> = listOf("*/*"),
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onPicked: (Context, GBDevice?, List<Uri>) -> Unit,
) : DeviceSetting()

/**
 * A preference that opens the system folder picker and stores the picked tree URI under [key].
 * The summary shows the stored URI.
 */
data class FolderPickerSetting(
    override val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int = 0,
    /** Takes a persistable read permission on the picked tree. */
    val persistUriPermission: Boolean = true,
    val dependency: String? = null,
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
    override val connectedOnly: Boolean = true,
    val onPicked: ((Context, GBDevice?, Uri) -> Unit)? = null,
) : DeviceSetting()

/**
 * Legacy wrapper for an existing DeviceSpecificSettingsScreen and its XML sub-screens, so
 * that a migrating coordinator can delegate remaining XML screens while providing model nodes for
 * others.
 */
data class XmlScreenSetting(
    val screen: DeviceSpecificSettingsScreen,
    val subScreens: List<Int> = emptyList(),
    override val connectedOnly: Boolean = true,
    /** Keys of preferences inside the sub-screen XML that should also be disabled when disconnected. */
    val childConnectedKeys: List<String> = emptyList(),
    override val visibleWhen: ((Prefs) -> Boolean)? = null,
) : DeviceSetting() {
    override val key: String get() = screen.key
}
