package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * One widget instance, placed on the dashboard: a [typeId] (maps to [GBWidget.id]) plus the
 * layout-level properties [WidgetLayoutStore] needs to draw the grid.
 */
data class WidgetInstance(
    val instanceId: String,
    val typeId: String,
    val columns: Int,
)

/**
 * Per-instance configuration, wrapping [instance]'s own SharedPreferences plus the dashboard-wide
 * device filter it inherits from by default.
 */
class WidgetConfig(
    val instance: WidgetInstance,
    val prefs: Prefs,
    private val dashboardShowAllDevices: Boolean,
    private val dashboardDeviceList: Set<String>,
) {
    /**
     * Resolves the devices this instance should show data for, filtered to those the widget supports.
     */
    fun resolveDevices(widget: GBWidget<*>): List<GBDevice> {
        val allDevices = GBApplication.app().deviceManager.devices
        val candidates = when (prefs.getString(KEY_DEVICES_MODE, DEVICES_MODE_INHERIT)) {
            DEVICES_MODE_ALL -> allDevices
            DEVICES_MODE_SELECTED -> {
                val selected = prefs.getStringSet(KEY_DEVICES, emptySet())
                allDevices.filter { selected.contains(it.address) }
            }

            else -> if (dashboardShowAllDevices) {
                allDevices
            } else {
                allDevices.filter { dashboardDeviceList.contains(it.address) }
            }
        }
        return candidates.filter { widget.isSupportedBy(it) }
    }

    /**
     * The instance's display title: a user override if set, otherwise the widget's own label.
     */
    fun title(context: Context, widget: GBWidget<*>): String {
        val custom = prefs.getString(KEY_TITLE, "")
        return custom.ifBlank { context.getString(widget.label) }
    }

    companion object {
        const val KEY_DEVICES_MODE = "widget_devices_mode"
        const val KEY_DEVICES = "widget_devices"
        const val KEY_COLUMNS = "widget_columns"
        const val KEY_TITLE = "widget_title"

        const val DEVICES_MODE_INHERIT = "inherit"
        const val DEVICES_MODE_ALL = "all"
        const val DEVICES_MODE_SELECTED = "selected"
    }
}
