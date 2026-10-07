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
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobeta.android.dslv.DragSortListPreference
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.SettingsRenderHost
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.util.UriUtils
import nodomain.freeyourgadget.gadgetbridge.util.XDatePreference
import nodomain.freeyourgadget.gadgetbridge.util.XTimePreference
import nodomain.freeyourgadget.gadgetbridge.util.preferences.GBSimpleSummaryProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

/**
 * Handle returned by [DeviceSettingRenderer.render]. Call [run] to refresh visibility and dynamic
 * entries; call [cleanup] in [androidx.preference.PreferenceFragmentCompat.onDestroyView] to
 * unregister any SharedPreferences listeners registered for callbacks.
 */
class DeviceSettingsRefreshHandle(
    private val sp: SharedPreferences,
    private val spListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>,
    private val refreshAction: Runnable,
) : Runnable {
    override fun run() = refreshAction.run()
    fun cleanup() = spListeners.forEach { sp.unregisterOnSharedPreferenceChangeListener(it) }
}

/**
 * Converts a list of [DeviceSetting] nodes into androidx [Preference] objects and adds them to
 * a parent [PreferenceGroup]. Returns a [DeviceSettingsRefreshHandle] that re-evaluates all
 * [DeviceSetting.visibleWhen] and screen [ScreenSetting.enabled] predicates, and repopulates all dynamic
 * [ListSetting.entriesProvider] when invoked. Call [DeviceSettingsRefreshHandle.cleanup] when the
 * fragment stops to unregister any SharedPreferences listeners.
 *
 * [CategorySetting] nodes are automatically hidden when all of their member preferences (those
 * between this category and the next one or an [XmlScreenSetting]) are invisible.
 */
object DeviceSettingRenderer {
    private val LOG: Logger = LoggerFactory.getLogger(DeviceSettingRenderer::class.java)

    fun render(
        items: List<DeviceSetting>,
        parent: PreferenceGroup,
        prefs: Prefs,
        handler: SettingsRenderHost,
    ): DeviceSettingsRefreshHandle {
        val visibilityPairs = mutableListOf<Pair<Preference, (Prefs) -> Boolean>>()
        val enabledPairs = mutableListOf<Pair<Preference, (Prefs) -> Boolean>>()
        val dynamicEntryPairs = mutableListOf<Pair<ListPreference, (Prefs) -> List<ListEntry>>>()
        val dynamicMultiEntryPairs = mutableListOf<Pair<MultiSelectListPreference, (Prefs) -> List<ListEntry>>>()
        val categoryMemberPairs = mutableListOf<Pair<PreferenceCategory, MutableList<Preference>>>()
        val summaryPairs = mutableListOf<Pair<Preference, (Context, Prefs) -> CharSequence?>>()
        val spListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        val mainHandler = Handler(Looper.getMainLooper())
        val sp: SharedPreferences = prefs.preferences

        val refreshRunnable = Runnable {
            LOG.debug("Refreshing device settings")

            val livePrefs = Prefs(sp)
            val context = handler.context
            visibilityPairs.forEach { (pref, predicate) -> pref.isVisible = predicate(livePrefs) }
            enabledPairs.forEach { (pref, predicate) -> pref.isEnabled = predicate(livePrefs) }
            dynamicEntryPairs.forEach { (pref, provider) ->
                applyEntries(pref, provider(livePrefs), context)
            }
            dynamicMultiEntryPairs.forEach { (pref, provider) ->
                applyEntries(pref, provider(livePrefs), context)
            }
            summaryPairs.forEach { (pref, provider) -> pref.summary = provider(context, livePrefs) }
            categoryMemberPairs.forEach { (cat, members) ->
                cat.isVisible = members.isNotEmpty() && members.any { it.isVisible }
            }
        }

        val postRefresh: () -> Unit = {
            mainHandler.post {
                refreshRunnable.run()
            }
        }

        renderItems(
            items,
            parent,
            prefs,
            handler,
            visibilityPairs,
            enabledPairs,
            dynamicEntryPairs,
            dynamicMultiEntryPairs,
            categoryMemberPairs,
            summaryPairs,
            spListeners,
            mainHandler,
            sp,
            postRefresh
        )

        // Initial visibility passes - individual predicates first, then category membership
        visibilityPairs.forEach { (pref, predicate) -> pref.isVisible = predicate(prefs) }
        enabledPairs.forEach { (pref, predicate) -> pref.isEnabled = predicate(prefs) }
        summaryPairs.forEach { (pref, provider) -> pref.summary = provider(handler.context, prefs) }
        categoryMemberPairs.forEach { (cat, members) ->
            cat.isVisible = members.isNotEmpty() && members.any { it.isVisible }
        }

        if (summaryPairs.isNotEmpty() || visibilityPairs.isNotEmpty() || enabledPairs.isNotEmpty()) {
            // A summaryProvider, visibility predicate, or enabled predicate can read any preference.
            // Refresh on every write so changes made outside this renderer (for example by a
            // device service action) are reflected in the rendered settings.
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> postRefresh() }
            spListeners.add(listener)
            sp.registerOnSharedPreferenceChangeListener(listener)
        }

        return DeviceSettingsRefreshHandle(sp, spListeners, refreshRunnable)
    }

    private fun applyEntries(pref: ListPreference, entries: List<ListEntry>, context: Context) {
        pref.entries = entries.map { entry ->
            when (entry) {
                is ListEntry.Res -> context.getString(entry.label)
                is ListEntry.Text -> entry.label
            }
        }.toTypedArray()
        pref.entryValues = entries.map { it.value }.toTypedArray()
    }

    private fun applyEntries(pref: MultiSelectListPreference, entries: List<ListEntry>, context: Context) {
        pref.entries = entries.map { entry ->
            when (entry) {
                is ListEntry.Res -> context.getString(entry.label)
                is ListEntry.Text -> entry.label
            }
        }.toTypedArray()
        pref.entryValues = entries.map { it.value }.toTypedArray()
    }

    /**
     * Summary provider used by [MultiSelectSetting] when no static [MultiSelectSetting.summary] is given:
     * a comma-delimited list of the currently selected entries' labels, matching [ListPreference.SimpleSummaryProvider]'s
     * behavior for single-select lists.
     */
    private fun multiSelectCommaSummaryProvider(context: Context) =
        Preference.SummaryProvider<MultiSelectListPreference> { pref ->
            val entries = pref.entries.orEmpty()
            val entryValues = pref.entryValues.orEmpty()
            val selected = entryValues.indices
                .filter { entryValues[it].toString() in pref.values }
                .map { entries[it] }
            if (selected.isEmpty()) context.getString(R.string.not_set) else selected.joinToString(", ")
        }

    /**
     * Summary provider used by [SortableListSetting] when no static [SortableListSetting.summary] is given:
     * a comma-delimited list of the selected entries' labels, in the selected order.
     */
    private fun sortableListSummaryProvider(context: Context) =
        Preference.SummaryProvider<DragSortListPreference> { pref ->
            val entries = pref.entries.orEmpty()
            val entryValues = pref.entryValues.orEmpty().map { it.toString() }
            val selected = DragSortListPreference.decodeValue(pref.value).orEmpty()
                .map { entryValues.indexOf(it.toString()) }
                .filter { it >= 0 }
                .map { entries[it] }
            if (selected.isEmpty()) context.getString(R.string.not_set) else selected.joinToString(", ")
        }

    private fun renderItems(
        items: List<DeviceSetting>,
        parent: PreferenceGroup,
        prefs: Prefs,
        handler: SettingsRenderHost,
        visibilityPairs: MutableList<Pair<Preference, (Prefs) -> Boolean>>,
        enabledPairs: MutableList<Pair<Preference, (Prefs) -> Boolean>>,
        dynamicEntryPairs: MutableList<Pair<ListPreference, (Prefs) -> List<ListEntry>>>,
        dynamicMultiEntryPairs: MutableList<Pair<MultiSelectListPreference, (Prefs) -> List<ListEntry>>>,
        categoryMemberPairs: MutableList<Pair<PreferenceCategory, MutableList<Preference>>>,
        summaryPairs: MutableList<Pair<Preference, (Context, Prefs) -> CharSequence?>>,
        spListeners: MutableList<SharedPreferences.OnSharedPreferenceChangeListener>,
        mainHandler: Handler,
        sp: SharedPreferences,
        postRefresh: () -> Unit,
    ) {
        LOG.debug("Rendering device settings")

        val context = handler.context

        for (setting in items) {
            LOG.debug("Rendering {} as {}", setting.key, setting.javaClass.simpleName)

            val pref: Preference = when (setting) {
                is CategorySetting -> {
                    val category = PreferenceCategory(context).apply {
                        key = setting.key
                        if (setting.titleText != null) title = setting.titleText else setTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        isIconSpaceReserved = setting.iconSpaceReserved
                    }
                    parent.addPreference(category)
                    renderItems(
                        setting.children,
                        category,
                        prefs,
                        handler,
                        visibilityPairs,
                        enabledPairs,
                        dynamicEntryPairs,
                        dynamicMultiEntryPairs,
                        categoryMemberPairs,
                        summaryPairs,
                        spListeners,
                        mainHandler,
                        sp,
                        postRefresh
                    )
                    val members = (0 until category.preferenceCount).map { category.getPreference(it) }.toMutableList()
                    if (members.isNotEmpty()) {
                        categoryMemberPairs.add(category to members)
                    }
                    if (setting.visibleWhen != null) {
                        visibilityPairs.add(category to setting.visibleWhen)
                    }
                    continue
                }

                is ScreenSetting -> {
                    val screen = parent.preferenceManager.createPreferenceScreen(context)
                    screen.key = setting.key
                    screen.setTitle(setting.title)
                    if (setting.summary != 0) screen.setSummary(setting.summary)
                    if (setting.icon != 0) screen.setIcon(setting.icon)
                    if (setting.enabled != null) {
                        val enabled = setting.enabled
                        enabledPairs.add(screen to enabled)
                    }
                    // Must be added to parent before rendering children so that dependencies can be resolved
                    parent.addPreference(screen)
                    renderItems(
                        setting.children,
                        screen,
                        prefs,
                        handler,
                        visibilityPairs,
                        enabledPairs,
                        dynamicEntryPairs,
                        dynamicMultiEntryPairs,
                        categoryMemberPairs,
                        summaryPairs,
                        spListeners,
                        mainHandler,
                        sp,
                        postRefresh
                    )
                    if (setting.visibleWhen != null) {
                        visibilityPairs.add(screen to setting.visibleWhen)
                    }
                    continue
                }

                is SwitchSetting -> {
                    SwitchPreferenceCompat(context).apply {
                        key = setting.key
                        setTitle(setting.title)
                        when {
                            setting.summaryOn != 0 || setting.summaryOff != 0 -> {
                                if (setting.summaryOn != 0) setSummaryOn(setting.summaryOn)
                                if (setting.summaryOff != 0) setSummaryOff(setting.summaryOff)
                            }

                            setting.summary != 0 -> setSummary(setting.summary)
                        }
                        if (setting.icon != 0) setIcon(setting.icon)
                        setDefaultValue(setting.defaultValue)
                        isEnabled = setting.enabled
                        disableDependentsState = setting.disableDependentsState
                        if (setting.confirmationMessage != 0) {
                            setOnPreferenceChangeListener { preference, newValue ->
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(context.getString(R.string.earfun_change_confirm_title, preference.title))
                                    .setMessage(setting.confirmationMessage)
                                    .setPositiveButton(R.string.ok) { _, _ ->
                                        (preference as SwitchPreferenceCompat).isChecked = newValue as Boolean
                                        handler.notifyPreferenceChanged(setting.key)
                                        postRefresh()
                                    }
                                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                                    .show()
                                false
                            }
                        } else {
                            setOnPreferenceChangeListener { _, _ ->
                                handler.notifyPreferenceChanged(setting.key)
                                postRefresh()
                                true
                            }
                        }
                    }
                }

                is ListSetting -> {
                    ListPreference(context).apply {
                        key = setting.key
                        setTitle(setting.title)
                        setDialogTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        when {
                            setting.entriesProvider != null -> {
                                applyEntries(this, setting.entriesProvider.invoke(prefs), context)
                                dynamicEntryPairs.add(this to setting.entriesProvider)
                            }

                            setting.entries.isNotEmpty() -> applyEntries(this, setting.entries, context)

                            else -> {
                                setEntries(setting.entriesRes)
                                setEntryValues(setting.entryValuesRes)
                            }
                        }
                        setDefaultValue(setting.defaultValue)
                        if (setting.summary != 0) {
                            setSummary(setting.summary)
                        } else {
                            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        }
                        setOnPreferenceChangeListener { pref, newValue ->
                            setting.onValueChange?.invoke(
                                pref,
                                (pref as ListPreference).value ?: setting.defaultValue,
                                newValue as String
                            )
                            handler.notifyPreferenceChanged(setting.key)
                            postRefresh()
                            true
                        }
                    }
                }

                is MultiSelectSetting -> {
                    MultiSelectListPreference(context).apply {
                        key = setting.key
                        setTitle(setting.title)
                        setDialogTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        if (setting.entriesProvider != null) {
                            applyEntries(this, setting.entriesProvider.invoke(prefs), context)
                            dynamicMultiEntryPairs.add(this to setting.entriesProvider)
                        } else {
                            applyEntries(this, setting.entries, context)
                        }
                        setDefaultValue(setting.defaultValue)
                        if (setting.summary != 0) {
                            setSummary(setting.summary)
                        } else {
                            summaryProvider = multiSelectCommaSummaryProvider(context)
                        }
                        setOnPreferenceChangeListener { _, _ ->
                            handler.notifyPreferenceChanged(setting.key)
                            postRefresh()
                            true
                        }
                    }
                }

                is SortableListSetting -> {
                    DragSortListPreference(context, null).apply {
                        key = setting.key
                        setTitle(setting.title)
                        setDialogTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        applyEntries(this, setting.entries, context)
                        setDefaultValue(setting.defaultValue.toTypedArray<CharSequence>())
                        if (setting.summary != 0) {
                            setSummary(setting.summary)
                        } else {
                            summaryProvider = sortableListSummaryProvider(context)
                        }
                        setOnPreferenceChangeListener { _, newValue ->
                            // DragSortListPreference also calls the listener from onSetInitialValue
                            val joined = (newValue as Array<*>).joinToString(",")
                            if (joined != prefs.getString(setting.key, setting.defaultValue.joinToString(","))) {
                                handler.notifyPreferenceChanged(setting.key)
                                postRefresh()
                            }
                            true
                        }
                    }
                }

                is SeekBarSetting -> {
                    object: SeekBarPreference(context){
                        override fun onSetInitialValue(defaultValue: Any?) {
                            super.onSetInitialValue(defaultValue)
                            if (setting.valueFormat != 0) {
                                summary = context.getString(setting.valueFormat, value * setting.scale)
                            }
                        }
                    }.apply {
                        key = setting.key
                        setTitle(setting.title)
                        if (setting.summary != 0) setSummary(setting.summary)
                        if (setting.icon != 0) setIcon(setting.icon)
                        max = setting.max
                        min = setting.min
                        setDefaultValue(setting.defaultValue)
                        showSeekBarValue = setting.showValue
                        seekBarIncrement = setting.step

                        setOnPreferenceChangeListener { pref, newValue ->
                            val raw = newValue as Int
                            val step = setting.step
                            val snapped = if (step > 1) {
                                setting.min + ((raw - setting.min).toDouble() / step).roundToInt() * step
                            } else {
                                raw
                            }

                            if (setting.valueFormat != 0) {
                                summary = context.getString(setting.valueFormat, snapped * setting.scale)
                            }

                            handler.notifyPreferenceChanged(setting.key)
                            postRefresh()

                            if (snapped != raw) {
                                (pref as SeekBarPreference).value = snapped
                                false
                            } else {
                                true
                            }
                        }

                        if (setting.onSharedPreferenceChanged != null) {
                            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, changedKey ->
                                if (changedKey == setting.key) {
                                    val newValue = sharedPrefs.getInt(changedKey, setting.defaultValue)
                                    mainHandler.post {
                                        value = newValue
                                        if (setting.valueFormat != 0) {
                                            summary = context.getString(setting.valueFormat, newValue * setting.scale)
                                        }
                                        setting.onSharedPreferenceChanged.invoke(newValue)
                                    }
                                }
                            }
                            spListeners.add(listener)
                            sp.registerOnSharedPreferenceChangeListener(listener)
                        }
                    }
                }

                is TextSetting -> {
                    EditTextPreference(context).apply {
                        key = setting.key
                        setTitle(setting.title)
                        setDialogTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        setDefaultValue(setting.defaultValue)
                        isEnabled = setting.enabled
                        if (setting.summary != 0) {
                            setSummary(setting.summary)
                        } else if (setting.defaultSummary != null && setting.summaryTemplate != 0) {
                            summaryProvider = GBSimpleSummaryProvider(
                                setting.defaultSummary.invoke(context),
                                setting.summaryTemplate,
                            )
                        } else {
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        }
                        setOnBindEditTextListener { editText ->
                            editText.inputType = setting.inputType
                            if (setting.maxLength != null) {
                                editText.filters = arrayOf<InputFilter>(LengthFilter(setting.maxLength))
                            }
                            setting.onBindEditText?.invoke(editText)
                        }
                        setOnPreferenceChangeListener { _, _ ->
                            handler.notifyPreferenceChanged(setting.key)
                            postRefresh()
                            true
                        }
                        if (setting.onSharedPreferenceChanged != null) {
                            val listener =
                                SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, changedKey ->
                                    if (changedKey == setting.key) {
                                        val newValue = sharedPrefs.getString(changedKey, setting.defaultValue)
                                            ?: setting.defaultValue
                                        mainHandler.post { setting.onSharedPreferenceChanged.invoke(newValue) }
                                    }
                                }
                            spListeners.add(listener)
                            sp.registerOnSharedPreferenceChangeListener(listener)
                        }
                    }
                }

                is ActionSetting -> {
                    Preference(context).apply {
                        key = setting.key
                        if (setting.titleText != null) title = setting.titleText
                        else if (setting.title != 0) setTitle(setting.title)
                        if (setting.summaryProvider != null) {
                            summaryPairs.add(this to setting.summaryProvider)
                        } else if (setting.summary != 0) {
                            setSummary(setting.summary)
                        }
                        if (setting.icon != 0) setIcon(setting.icon)
                        isPersistent = false
                        isEnabled = setting.enabled
                        if (setting.confirmationMessage != 0) {
                            setOnPreferenceClickListener { preference ->
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(preference.title)
                                    .setMessage(setting.confirmationMessage)
                                    .setPositiveButton(R.string.ok) { _, _ ->
                                        setting.onClick?.invoke(handler.context, handler.device)
                                    }
                                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                                    .show()
                                true
                            }
                        } else {
                            setOnPreferenceClickListener {
                                setting.onClick?.invoke(handler.context, handler.device) ?: false
                            }
                        }
                    }
                }

                is InfoSetting -> {
                    Preference(context).apply {
                        key = setting.key
                        if (setting.titleText != null) title = setting.titleText
                        else if (setting.title != 0) setTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        isIconSpaceReserved = setting.iconSpaceReserved
                        isPersistent = false
                        val pref = this
                        when {
                            setting.summaryProvider != null ->
                                summaryPairs.add(pref to setting.summaryProvider)

                            setting.summary != 0 -> setSummary(setting.summary)

                            else -> {
                                pref.summary = prefs.getString(setting.key, setting.defaultValue)
                                val listener =
                                    SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, changedKey ->
                                        if (changedKey == setting.key) {
                                            val newValue = sharedPrefs.getString(changedKey, setting.defaultValue)
                                                ?: setting.defaultValue
                                            mainHandler.post { pref.summary = newValue }
                                        }
                                    }
                                spListeners.add(listener)
                                sp.registerOnSharedPreferenceChangeListener(listener)
                            }
                        }
                    }
                }

                is TimeSetting -> {
                    XTimePreference(context, null).apply {
                        key = setting.key
                        setTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        setDefaultValue(setting.defaultValue)

                        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, changedKey ->
                            if (changedKey == setting.key) {
                                val newValue = sharedPrefs.getString(changedKey, setting.defaultValue)
                                    ?: setting.defaultValue
                                mainHandler.post {
                                    handler.notifyPreferenceChanged(setting.key)
                                    postRefresh()
                                    setting.onSharedPreferenceChanged?.invoke(newValue)
                                }
                            }
                        }
                        spListeners.add(listener)
                        sp.registerOnSharedPreferenceChangeListener(listener)
                    }
                }

                is DateSetting -> {
                    XDatePreference(context, null).apply {
                        key = setting.key
                        setTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        setMinDate(setting.minDate)
                        setMaxDate(setting.maxDate)
                        setDefaultValue(setting.defaultValue)

                        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, changedKey ->
                            if (changedKey == setting.key) {
                                val newValue = sharedPrefs.getString(changedKey, setting.defaultValue)
                                    ?: setting.defaultValue
                                mainHandler.post {
                                    handler.notifyPreferenceChanged(setting.key)
                                    postRefresh()
                                    setting.onSharedPreferenceChanged?.invoke(newValue)
                                }
                            }
                        }
                        spListeners.add(listener)
                        sp.registerOnSharedPreferenceChangeListener(listener)
                    }
                }

                is FilePickerSetting -> {
                    val mimeTypes = setting.mimeTypes.toTypedArray()
                    // The launcher must be registered before the fragment is started, so it is
                    // created here rather than on click.
                    val launcher = handler.registerForActivityResult(
                        ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        if (uris.isNotEmpty()) {
                            setting.onPicked(handler.context, handler.device, uris)
                        }
                    }

                    Preference(context).apply {
                        key = setting.key
                        setTitle(setting.title)
                        if (setting.summary != 0) setSummary(setting.summary)
                        if (setting.icon != 0) setIcon(setting.icon)
                        isPersistent = false
                        setOnPreferenceClickListener {
                            launcher.launch(mimeTypes)
                            true
                        }
                    }
                }

                is FolderPickerSetting -> {
                    val folderPref = Preference(context)
                    val launcher = handler.registerForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            if (setting.persistUriPermission) {
                                handler.context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                            sp.edit().putString(setting.key, uri.toString()).apply()
                            folderPref.summary = UriUtils.resolveLocationSummary(handler.context, uri.toString())
                            setting.onPicked?.invoke(handler.context, handler.device, uri)
                            postRefresh()
                        }
                    }

                    folderPref.apply {
                        key = setting.key
                        setTitle(setting.title)
                        if (setting.icon != 0) setIcon(setting.icon)
                        isPersistent = false
                        summary = UriUtils.resolveLocationSummary(context, prefs.getString(setting.key, ""))
                        setOnPreferenceClickListener {
                            launcher.launch(null)
                            true
                        }
                    }
                }

                is XmlScreenSetting -> {
                    // Inflate the root XML entry at the correct position so it appears in the
                    // order declared in the DSL rather than being appended at the end.
                    handler.addXmlPreferences(setting.screen.xml)
                    continue
                }
            }

            parent.addPreference(pref)

            // Dependency must be set after addPreference
            val dependency: String? = when (setting) {
                is SwitchSetting -> setting.dependency
                is ListSetting -> setting.dependency
                is MultiSelectSetting -> setting.dependency
                is SortableListSetting -> setting.dependency
                is SeekBarSetting -> setting.dependency
                is TextSetting -> setting.dependency
                is ActionSetting -> setting.dependency
                is InfoSetting -> setting.dependency
                is TimeSetting -> setting.dependency
                is DateSetting -> setting.dependency
                is FilePickerSetting -> setting.dependency
                is FolderPickerSetting -> setting.dependency
            }
            if (dependency != null) pref.dependency = dependency

            if (setting.visibleWhen != null) {
                visibilityPairs.add(pref to setting.visibleWhen!!)
            }
        }
    }
}
