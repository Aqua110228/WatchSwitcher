package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * A dashboard widget. Implementations are stateless Kotlin `object`s; all mutable state lives in
 * the [WidgetInstance]/[WidgetConfig].
 * <p>
 * A widget can be instantiated multiple times as separate [WidgetInstance]s, each with its own device
 * selection and settings (see [WidgetConfig]) -- see [WidgetRegistry] for how instances and kinds
 * are resolved together, and [WidgetLayoutStore] for how the placed instances are persisted.
 */
interface GBWidget<D> {
    /**
     * Stable id persisted in the layout ([WidgetInstance.typeId]). Should never change.
     */
    val id: String

    /**
     * The widget label, which can be displayed on the widget itself.
     */
    @get:StringRes
    val label: Int

    /**
     * The human-readable name, in the widget type list. Might include distinction between different widgets for the
     * same data type. Defaults to the widget label.
     */
    @get:StringRes
    val name: Int get() = label

    @get:DrawableRes
    val icon: Int

    /**
     * Default column span for a newly placed instance. Height is intrinsic to the view.
     */
    val defaultColumns: Int get() = 1

    /**
     * Column spans the user can choose in the settings; a single element means fixed size.
     */
    val allowedColumns: List<Int> get() = listOf(1)

    /**
     * Whether this widget is supported by the [device].
     */
    fun isSupportedBy(device: GBDevice): Boolean

    /**
     * Per-instance settings, rendered with the same DSL used for device settings (see
     * [nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings]).
     * Common settings (device selection, column span, title) are prepended by the settings
     * screen; this only needs to declare widget-specific options.
     * Returns null if the widget has none.
     */
    fun settings(context: Context): DeviceSettingsSpec? = null

    /**
     * Loads the data this widget needs to render. Runs on a background dispatcher via
     * [WidgetDataScope]; must not touch views or hold a [Context] past this call returning.
     * [config] gives access to this instance's instance and its own settings.
     */
    suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): D

    /**
     * Inflates an empty view for this widget kind. Called once per RecyclerView view holder.
     */
    fun createView(inflater: LayoutInflater, parent: ViewGroup): View

    /**
     * Binds [data] (from [loadData]) onto a view created by [createView]. Runs on the UI thread.
     */
    fun bind(view: View, config: WidgetConfig, data: D)

    /**
     * Optional click target for the whole widget, e.g. opening a chart. No-op by default.
     * [timestamp] is the epoch second of the dashboard's currently selected day.
     */
    fun onClick(view: View, config: WidgetConfig, timestamp: Int) {}
}

/**
 * Calls [GBWidget.bind] on a star-projected `GBWidget<*>` with a [data] value that is trusted
 * to have come from that same widget's [GBWidget.loadData]. This is known to always be true,
 * since `DashboardViewModel` pairs each loaded value with the widget it loaded it from.
 */
@Suppress("UNCHECKED_CAST")
fun <D> GBWidget<D>.bindUnsafe(view: View, config: WidgetConfig, data: Any?) {
    bind(view, config, data as D)
}
