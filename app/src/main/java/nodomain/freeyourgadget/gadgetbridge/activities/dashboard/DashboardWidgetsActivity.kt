package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.DashboardFragment
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import java.util.Collections

/**
 * Lists the currently configured dashboard widget instances: drag to reorder, and per-row
 * Configure/Duplicate/Remove. A FAB adds a new instance of any widget kind supported by a
 * currently paired device.
 */
class DashboardWidgetsActivity : AbstractGBActivity() {
    private lateinit var adapter: InstanceAdapter
    private var instances: MutableList<WidgetInstance> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_widgets)

        adapter = InstanceAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.dashboard_widgets_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                rv: RecyclerView,
                holder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = holder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                Collections.swap(instances, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not swipeable; only long-press drag reorders.
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                WidgetLayoutStore.save(instances)
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showAddWidgetDialog()
        }

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DashboardFragment.ACTION_CONFIG_CHANGE))
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reload() {
        instances = WidgetLayoutStore.load().toMutableList()
        adapter.submit(instances)
    }

    private fun showAddWidgetDialog() {
        val available = WidgetRegistry.available().sortedBy { it.name }
        if (available.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.widgets_add_widget)
                .setMessage(R.string.no_supported_devices_found)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = available.map { getString(it.name) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_add_widget)
            .setItems(labels) { _, which ->
                WidgetLayoutStore.add(available[which].id)
                reload()
            }
            .show()
    }

    private fun configure(instance: WidgetInstance) {
        WidgetInstanceSettingsActivity.startForWidget(this, instance)
    }

    private fun duplicate(instance: WidgetInstance) {
        WidgetLayoutStore.duplicate(instance.instanceId)
        reload()
    }

    private fun remove(instance: WidgetInstance) {
        WidgetLayoutStore.remove(instance.instanceId)
        reload()
    }

    private fun showOverflowMenu(anchor: View, instance: WidgetInstance, hasWidget: Boolean) {
        val popup = PopupMenu(this, anchor)
        if (hasWidget) popup.menu.add(0, MENU_CONFIGURE, 0, R.string.app_configure)
        popup.menu.add(0, MENU_DUPLICATE, 1, R.string.widgets_duplicate)
        popup.menu.add(0, MENU_REMOVE, 2, R.string.widgets_remove)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CONFIGURE -> {
                    configure(instance)
                    true
                }

                MENU_DUPLICATE -> {
                    duplicate(instance)
                    true
                }

                MENU_REMOVE -> {
                    remove(instance)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private inner class InstanceAdapter : RecyclerView.Adapter<InstanceAdapter.ViewHolder>() {
        var touchHelper: ItemTouchHelper? = null
        private var items: List<WidgetInstance> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<WidgetInstance>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.widget_instance_icon)
            val title: TextView = view.findViewById(R.id.widget_instance_title)
            val overflow: ImageButton = view.findViewById(R.id.widget_instance_overflow)
            val dragHandle: View = view.findViewById(R.id.widget_instance_drag_handle)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dashboard_widget_instance, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val instance = items[position]
            val widget: GBWidget<*>? = WidgetRegistry.byId(instance.typeId)

            if (widget == null) {
                // Should never happen
                holder.icon.setImageResource(R.drawable.ic_warning)
                holder.title.text = "NULL (${instance.typeId})"
            } else {
                holder.icon.setImageResource(widget.icon)
                val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId))
                val customTitle = prefs.getString(WidgetConfig.KEY_TITLE, "")
                holder.title.text = customTitle.ifBlank { holder.itemView.context.getString(widget.name) }
            }

            holder.overflow.setOnClickListener { anchor -> showOverflowMenu(anchor, instance, widget != null) }
            holder.itemView.setOnClickListener {
                if (widget != null) configure(instance)
            }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(holder)
                }
                false
            }
        }
    }

    companion object {
        private const val MENU_CONFIGURE = 1
        private const val MENU_DUPLICATE = 2
        private const val MENU_REMOVE = 3
    }
}
