package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.database.repository.WorkoutTemplateRepository
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorkoutTemplateListBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat

/**
 * Lists the [WorkoutTemplate]s of [device]. A template that the [WorkoutTemplateSpec] does not
 * support is displayed, but it cannot be sent to the device.
 */
class WorkoutTemplateListActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var spec: WorkoutTemplateSpec
    private lateinit var adapter: TemplateAdapter
    private val viewModel: WorkoutTemplateListViewModel by viewModels()

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    private val syncStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dev = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (dev == null) {
            finish()
            return
        }
        val deviceSpec = dev.deviceCoordinator.getWorkoutTemplateSpec(dev)
        if (deviceSpec == null) {
            finish()
            return
        }
        device = dev
        spec = deviceSpec

        val binding = ActivityWorkoutTemplateListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TemplateAdapter()
        binding.workoutTemplateList.apply {
            layoutManager = LinearLayoutManager(this@WorkoutTemplateListActivity)
            adapter = this@WorkoutTemplateListActivity.adapter
        }

        binding.fab.setOnClickListener {
            showAddDialog()
        }

        lifecycleScope.launch {
            viewModel.items.collect { items ->
                adapter.submit(items)
                binding.workoutTemplateListEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Reading the exercise catalog is slow, and the edit screens read it on the main thread
        spec.exerciseCatalog?.let { catalog ->
            lifecycleScope.launch { withContext(Dispatchers.IO) { catalog.preload() } }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            syncStateReceiver,
            IntentFilter(WorkoutTemplateRepository.ACTION_SYNC_STATE_CHANGED)
        )
        refresh()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStateReceiver)
        super.onPause()
    }

    private fun refresh() {
        viewModel.refresh(device, spec)
    }

    private fun showAddDialog() {
        val sports = spec.sports.keys.sortedBy { it.getLabel(this) }
        if (sports.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.workout_template_add)
                .setMessage(R.string.workout_template_no_sports_supported)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_add)
            .setAdapter(SportPickerAdapter(this, sports)) { _, which ->
                openEditor(
                    activityKind = sports[which],
                    templateId = null
                )
            }
            .show()
    }

    private fun openEditor(activityKind: ActivityKind?, templateId: Long?) {
        val editIntent = Intent(this, WorkoutTemplateEditActivity::class.java)
        editIntent.putExtra(GBDevice.EXTRA_DEVICE, device)
        if (templateId != null) {
            editIntent.putExtra(WorkoutTemplateEditActivity.EXTRA_TEMPLATE_ID, templateId)
        } else if (activityKind != null) {
            editIntent.putExtra(WorkoutTemplateEditActivity.EXTRA_ACTIVITY_KIND, activityKind.code)
        }
        editLauncher.launch(editIntent)
    }

    private fun sync(item: WorkoutTemplateListItem) {
        val id = item.template.id ?: return
        if (!device.isInitialized) {
            GB.toast(this, getString(R.string.device_not_connected), Toast.LENGTH_SHORT, GB.WARN)
            return
        }
        GBApplication.deviceService(device).onSyncWorkoutTemplate(id)
        GB.toast(
            this,
            getString(R.string.workout_template_sync_started, item.template.name),
            Toast.LENGTH_SHORT,
            GB.INFO
        )
    }

    private fun confirmDelete(item: WorkoutTemplateListItem) {
        val id = item.template.id ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.workout_template_delete_confirm, item.template.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(id, device, spec) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun duplicate(item: WorkoutTemplateListItem) {
        val id = item.template.id ?: return
        val input = EditText(this).apply {
            setText(getString(R.string.workout_template_copy_name, item.template.name))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_duplicate)
            .setView(input)
            .setPositiveButton(R.string.widgets_duplicate) { _, _ ->
                val name = input.text.toString().ifBlank { item.template.name }
                viewModel.duplicate(id, name, device, spec)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showOverflowMenu(anchor: View, item: WorkoutTemplateListItem) {
        val popup = PopupMenu(this, anchor)
        if (item.supported) {
            popup.menu.add(0, MENU_SYNC, 0, R.string.workout_template_sync)
        }
        popup.menu.add(0, MENU_EDIT, 1, R.string.appmanager_app_edit)
        popup.menu.add(0, MENU_DUPLICATE, 2, R.string.widgets_duplicate)
        popup.menu.add(0, MENU_DELETE, 3, R.string.delete)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_SYNC -> {
                    sync(item)
                    true
                }

                MENU_EDIT -> {
                    openEditor(activityKind = null, templateId = item.template.id)
                    true
                }

                MENU_DUPLICATE -> {
                    duplicate(item)
                    true
                }

                MENU_DELETE -> {
                    confirmDelete(item)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private inner class TemplateAdapter : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {
        private var items: List<WorkoutTemplateListItem> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<WorkoutTemplateListItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.workout_template_icon)
            val name: TextView = view.findViewById(R.id.workout_template_name)
            val summary: TextView = view.findViewById(R.id.workout_template_summary)
            val syncStatus: TextView = view.findViewById(R.id.workout_template_sync_status)
            val sync: ImageButton = view.findViewById(R.id.workout_template_sync)
            val overflow: ImageButton = view.findViewById(R.id.workout_template_overflow)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workout_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context

            holder.icon.setImageResource(item.template.activityKind.icon)
            holder.name.text = item.template.name
            val stepCount = item.template.totalStepCount()
            holder.summary.text =
                context.resources.getQuantityString(R.plurals.workout_template_step_count, stepCount, stepCount)
            if (item.supported) {
                val status = context.getString(item.syncStatus.label)
                holder.syncStatus.text = item.syncError?.let { "$status: $it" } ?: status
            } else {
                holder.syncStatus.text = context.getString(R.string.workout_template_unsupported)
            }
            holder.sync.visibility = if (item.supported) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { openEditor(activityKind = null, templateId = item.template.id) }
            holder.sync.setOnClickListener { sync(item) }
            holder.overflow.setOnClickListener { anchor -> showOverflowMenu(anchor, item) }
        }
    }

    companion object {
        private const val MENU_SYNC = 1
        private const val MENU_EDIT = 2
        private const val MENU_DUPLICATE = 3
        private const val MENU_DELETE = 4
    }
}
