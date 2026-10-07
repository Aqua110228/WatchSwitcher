package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import nodomain.freeyourgadget.gadgetbridge.widgets.bindUnsafe

/**
 * Renders the placed [WidgetInstance]s. Each item is a [MaterialCardView] whose single
 * [FrameLayout] slot hosts the plain [View] produced by the [GBWidget].
 * <p>
 * Not a `RecyclerView.Adapter` with per-kind view types: a dashboard has at most a few dozen
 * items, so cross-type recycling isn't worth the complexity. [onBindViewHolder] does still reuse
 * a holder's existing content view when re-binding the same widget type in place (e.g. when its
 * data finishes loading).
 */
class DashboardAdapter : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    class ViewHolder(val card: MaterialCardView, val slot: FrameLayout) : RecyclerView.ViewHolder(card) {
        /**
         * The typeId currently inflated into [slot], so [onBindViewHolder] can reuse it as-is.
         */
        var boundTypeId: String? = null
        var contentView: View? = null
    }

    private var instances: List<WidgetInstance> = emptyList()
    private var states: Map<String, WidgetState> = emptyMap()

    /**
     * Backs [getItemId]: a persistent numeric id per instanceId for stable IDs.
     */
    private val stableIds = mutableMapOf<String, Long>()
    private var nextStableId = 0L

    /**
     * The dashboard-wide device filter, inherited by instances whose own filter mode is "inherit".
     */
    var dashboardShowAllDevices: Boolean = true
    var dashboardDeviceList: Set<String> = emptySet()

    /**
     * The epoch second of the currently selected dashboard day.
     */
    var timestamp: Int = 0

    var cardsEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /**
     * Replaces the placed instances. Call after [WidgetLayoutStore][nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore] changes.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun submitInstances(newInstances: List<WidgetInstance>) {
        instances = newInstances
        notifyDataSetChanged()
    }

    /**
     * Updates the load state for the current instances. Notifies only the instances whose state
     * actually changed. This fires once per widget as each finishes loading independently.
     */
    fun submitStates(newStates: Map<String, WidgetState>) {
        val oldStates = states
        states = newStates
        for (position in instances.indices) {
            val instanceId = instances[position].instanceId
            if (oldStates[instanceId] != newStates[instanceId]) {
                notifyItemChanged(position)
            }
        }
    }

    fun columnsAt(position: Int): Int = instances.getOrNull(position)?.columns ?: 1

    override fun getItemCount(): Int = instances.size

    override fun getItemId(position: Int): Long = stableIdFor(instances[position].instanceId)

    private fun stableIdFor(instanceId: String): Long = stableIds.getOrPut(instanceId) { nextStableId++ }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = LayoutInflater.from(parent.context).inflate(
            R.layout.dashboard_widget_card,
            parent,
            false
        ) as MaterialCardView
        val slot = card.findViewById<FrameLayout>(R.id.dashboard_widget_content)
        return ViewHolder(card, slot)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val instance = instances[position]

        applyCardStyle(holder.card)

        // Unknown or currently-unavailable widget type - render it empty
        val widget = WidgetRegistry.byId(instance.typeId)
        if (widget == null) {
            if (holder.boundTypeId != null) {
                holder.slot.removeAllViews()
                holder.boundTypeId = null
                holder.contentView = null
            }
            return
        }

        // Reuse the existing content view when this holder is already showing the same widget
        // type (the common case: a re-bind because that instance's data just finished loading,
        // or a data refresh for a new day) rather than tearing it down and reinflating it.
        val view: View
        if (holder.boundTypeId == instance.typeId && holder.contentView != null) {
            view = holder.contentView!!
        } else {
            holder.slot.removeAllViews()
            view = widget.createView(LayoutInflater.from(holder.slot.context), holder.slot)
            holder.slot.addView(view)
            holder.boundTypeId = instance.typeId
            holder.contentView = view
        }

        val state = states[instance.instanceId]
        if (state is WidgetState.Ready) {
            val config = configFor(instance)
            widget.bindUnsafe(view, config, state.data)
            view.setOnClickListener { widget.onClick(view, config, timestamp) }
            view.setOnLongClickListener {
                WidgetInstanceSettingsActivity.startForWidget(view.context, instance)
                true
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.slot.removeAllViews()
        holder.boundTypeId = null
        holder.contentView = null
    }

    private fun configFor(instance: WidgetInstance): WidgetConfig {
        val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId))
        return WidgetConfig(instance, prefs, dashboardShowAllDevices, dashboardDeviceList)
    }

    private fun applyCardStyle(card: MaterialCardView) {
        val density = card.resources.displayMetrics.density
        card.strokeWidth = 0
        if (cardsEnabled) {
            // Same fill as the stat tiles, so it follows the theme.
            card.setCardBackgroundColor(MaterialColors.getColor(card, R.attr.stat_tile_bg))
            card.radius = 4 * density
            card.cardElevation = 4 * density
        } else {
            card.setCardBackgroundColor(Color.TRANSPARENT)
            card.radius = 0f
            card.cardElevation = 0f
        }
    }
}
