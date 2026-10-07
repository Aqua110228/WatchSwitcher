package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.SettingsRenderHost
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingRenderer
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsRefreshHandle
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry

/**
 * Renders one placed widget instance's settings: the common ones (device selection, column span, title override),
 * followed by the widget's own [GBWidget.settings].
 */
class WidgetInstanceSettingsFragment : AbstractPreferenceFragment(), SettingsRenderHost {
    private var refreshHandle: DeviceSettingsRefreshHandle? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val instanceId = requireArguments().getString(ARG_INSTANCE_ID) ?: return
        val typeId = requireArguments().getString(ARG_TYPE_ID) ?: return
        val widget = WidgetRegistry.byId(typeId)!!

        preferenceManager.sharedPreferencesName = "widgetsettings_$instanceId"

        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instanceId))
        val items = mutableListOf<DeviceSetting>()
        items += commonSettings(widget)
        widget.settings(requireContext())?.let {
            items += it.items
        }

        refreshHandle = DeviceSettingRenderer.render(items, screen, prefs, this)

        setupColumnsPreference(instanceId, widget)
    }

    /**
     * [WidgetConfig.KEY_COLUMNS] renders as a normal [ListPreference], but its actual value comes from
     * [WidgetLayoutStore] (the layout, not this instance's own settings file) since it's what
     * `DashboardAdapter.columnsAt` reads for the grid's span. So its current value is seeded from
     * there instead of from `SharedPreferences`, and changes are additionally written back there.
     */
    private fun setupColumnsPreference(instanceId: String, widget: GBWidget<*>) {
        val columnsPref = findPreference<ListPreference>(WidgetConfig.KEY_COLUMNS) ?: return
        val currentInstance = WidgetLayoutStore.load().find { it.instanceId == instanceId }
        columnsPref.value = (currentInstance?.columns ?: widget.defaultColumns).toString()

        val rendererListener = columnsPref.onPreferenceChangeListener
        columnsPref.setOnPreferenceChangeListener { preference, newValue ->
            val accepted = rendererListener?.onPreferenceChange(preference, newValue) ?: true
            if (accepted) {
                val columns = (newValue as? String)?.toIntOrNull() ?: widget.defaultColumns
                WidgetLayoutStore.setColumns(instanceId, columns)
            }
            accepted
        }
    }

    override fun onDestroyView() {
        refreshHandle?.cleanup()
        refreshHandle = null
        super.onDestroyView()
    }

    private fun commonSettings(widget: GBWidget<*>): List<DeviceSetting> = deviceSettings {
        list(
            key = WidgetConfig.KEY_DEVICES_MODE,
            title = R.string.bottom_nav_devices,
            icon = R.drawable.ic_devices_wearables,
            entries = listOf(
                ListEntry.Res(WidgetConfig.DEVICES_MODE_INHERIT, R.string.pref_widget_devices_mode_inherit),
                ListEntry.Res(WidgetConfig.DEVICES_MODE_ALL, R.string.pref_dashboard_all_devices_title),
                ListEntry.Res(WidgetConfig.DEVICES_MODE_SELECTED, R.string.pref_auto_export_gpx_selected_devices),
            ),
            defaultValue = WidgetConfig.DEVICES_MODE_INHERIT,
        )
        multiSelect(
            key = WidgetConfig.KEY_DEVICES,
            title = R.string.pref_dashboard_select_devices_title,
            icon = R.drawable.ic_devices_other,
            entriesProvider = {
                GBApplication.app().deviceManager.devices
                    .filter { widget.isSupportedBy(it) }
                    .sortedBy { it.aliasOrName }
                    .map { ListEntry.Text(it.address, it.aliasOrName) }
            },
            visibleWhen = {
                it.getString(
                    WidgetConfig.KEY_DEVICES_MODE,
                    WidgetConfig.DEVICES_MODE_INHERIT
                ) == WidgetConfig.DEVICES_MODE_SELECTED
            },
        )
        if (widget.allowedColumns.size > 1) {
            list(
                key = WidgetConfig.KEY_COLUMNS,
                title = R.string.pref_widget_columns_title,
                icon = R.drawable.ic_font_size,
                entries = widget.allowedColumns.map {
                    ListEntry.Res(
                        it.toString(),
                        if (it >= 2) R.string.pref_widget_columns_two else R.string.pref_widget_columns_one
                    )
                },
                defaultValue = widget.defaultColumns.toString(),
            )
        }
        text(
            key = WidgetConfig.KEY_TITLE,
            title = R.string.pref_widget_title_title,
            summary = R.string.pref_widget_title_summary,
            icon = R.drawable.ic_label_24px,
            defaultValue = "",
            inputType = InputType.TYPE_CLASS_TEXT,
            connectedOnly = false,
        )
    }.items

    //
    // SettingsRenderHost
    //
    // findPreference(CharSequence) and getContext() are already provided by
    // PreferenceFragmentCompat/Fragment with compatible signatures.
    //

    override fun addPreferenceHandlerFor(preferenceKey: String) {
        // No device to send configuration to.
    }

    override fun addPreferenceHandlerFor(preferenceKey: String, extraListener: Preference.OnPreferenceChangeListener) {
        findPreference<Preference>(preferenceKey)?.onPreferenceChangeListener = extraListener
    }

    override fun notifyPreferenceChanged(preferenceKey: String) {
        // No device to notify - the renderer already re-evaluates visibleWhen/entriesProvider
        // itself after every change.
    }

    override fun setInputTypeFor(preferenceKey: String, editTypeFlags: Int) {
        (findPreference<Preference>(preferenceKey) as? EditTextPreference)?.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = editTypeFlags
        }
    }

    override fun navigateToScreen(screen: PreferenceScreen) {
        onNavigateToScreen(screen)
    }

    override fun addXmlPreferences(resId: Int) {
        addPreferencesFromResource(resId)
    }

    companion object {
        const val ARG_INSTANCE_ID = "widget_instance_id"
        const val ARG_TYPE_ID = "widget_type_id"

        fun newInstance(instanceId: String, typeId: String): WidgetInstanceSettingsFragment {
            val fragment = WidgetInstanceSettingsFragment()
            val args = Bundle()
            args.putString(ARG_INSTANCE_ID, instanceId)
            args.putString(ARG_TYPE_ID, typeId)
            fragment.arguments = args
            return fragment
        }
    }
}
