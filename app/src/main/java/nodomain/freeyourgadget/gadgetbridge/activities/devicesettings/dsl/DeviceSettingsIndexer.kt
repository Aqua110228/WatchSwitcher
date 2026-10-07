package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl

import android.content.Context
import androidx.annotation.StringRes
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Builds a [SearchConfiguration] index directly from a [DeviceSettingsSpec], bypassing XML.
 * <p>
 * Unlike [SearchConfiguration.index], which parses a static preference XML file, this walks the
 * DSL tree so that entries hidden by [DeviceSetting.visibleWhen] are correctly left out of
 * the index, and list/multi-select entry labels are resolved the same way the renderer resolves
 * them (including device-reported [ListSetting.entriesProvider] values).
 */
object DeviceSettingsIndexer {

    /**
     * Indexes every visible, labeled [DeviceSetting] in [items] into [searchConfiguration].
     */
    fun index(
        context: Context,
        searchConfiguration: SearchConfiguration,
        items: List<DeviceSetting>,
        prefs: Prefs,
    ) {
        indexItems(context, searchConfiguration, items, prefs, emptyList())
    }

    private fun indexItems(
        context: Context,
        searchConfiguration: SearchConfiguration,
        items: List<DeviceSetting>,
        prefs: Prefs,
        breadcrumbs: List<String>,
    ) {
        for (item in items) {
            if (item.visibleWhen?.invoke(prefs) == false) {
                continue
            }
            when (item) {
                is ScreenSetting -> {
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs, null)
                    indexItems(
                        context,
                        searchConfiguration,
                        item.children,
                        prefs,
                        breadcrumbs + context.getString(item.title)
                    )
                }

                is CategorySetting ->
                    indexItems(
                        context,
                        searchConfiguration,
                        item.children,
                        prefs,
                        breadcrumbs + (item.titleText ?: context.getString(item.title))
                    )

                is SwitchSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs, null)

                is ListSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs,
                        entriesLabel(QuickSettings.resolveEntries(context, item, prefs), context)
                    )

                is MultiSelectSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs,
                        entriesLabel(resolveMultiSelectEntries(item, prefs), context)
                    )

                is SortableListSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs,
                        entriesLabel(item.entries, context)
                    )

                is SeekBarSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs, null)

                is TextSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), item.summary, breadcrumbs, null)

                is InfoSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, item.titleText ?: title(context, item.title),
                        item.summary, breadcrumbs, null
                    )

                is ActionSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, item.titleText ?: title(context, item.title),
                        item.summary, breadcrumbs, null
                    )

                is TimeSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), 0, breadcrumbs, null)

                is DateSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), 0, breadcrumbs, null)

                is FilePickerSetting ->
                    indexSingle(
                        context, searchConfiguration, item.key, title(context, item.title),
                        item.summary, breadcrumbs, null
                    )

                is FolderPickerSetting ->
                    indexSingle(context, searchConfiguration, item.key, title(context, item.title), 0, breadcrumbs, null)

                // Indexed from its own XML resource instead, see DeviceSpecificSettingsFragment.
                is XmlScreenSetting -> {}
            }
        }
    }

    private fun resolveMultiSelectEntries(setting: MultiSelectSetting, prefs: Prefs): List<ListEntry> =
        setting.entriesProvider?.invoke(prefs) ?: setting.entries

    private fun entriesLabel(entries: List<ListEntry>, context: Context): String =
        entries.joinToString(", ") { entry ->
            when (entry) {
                is ListEntry.Res -> context.getString(entry.label)
                is ListEntry.Text -> entry.label
            }
        }

    private fun title(context: Context, @StringRes titleRes: Int): String? =
        if (titleRes != 0) context.getString(titleRes) else null

    private fun indexSingle(
        context: Context,
        searchConfiguration: SearchConfiguration,
        key: String,
        title: String?,
        summaryRes: Int,
        breadcrumbs: List<String>,
        entries: String?,
    ) {
        if (title == null) {
            return
        }
        val preferenceItem = searchConfiguration.indexItem()
            .withKey(key)
            .withTitle(title)
        if (summaryRes != 0) {
            preferenceItem.withSummary(context.getString(summaryRes))
        }
        if (!entries.isNullOrEmpty()) {
            preferenceItem.withEntries(entries)
        }
        breadcrumbs.forEach { preferenceItem.addBreadcrumb(it) }
    }
}
