package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractListActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ActivitySummariesFilter
import nodomain.freeyourgadget.gadgetbridge.adapter.WorkoutSummariesAdapter
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.WorkoutFilterUtils
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectWorkoutSync
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.applyAccentColors
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getSerializableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.BitSet
import java.util.Calendar
import java.util.Locale
import androidx.core.view.get
import androidx.core.view.size

class WorkoutListActivity : AbstractListActivity<BaseActivitySummary>() {
    private val viewModel: WorkoutListViewModel by viewModels()

    private var activityKindMap = HashMap<String, ActivityKind>(0)
    private var activityFilter = 0
    private var dateFromFilter = 0L
    private var dateToFilter = 0L
    private var deviceFilter = 0L
    private var itemsFilter: List<Long>? = null
    private var nameContainsFilter: String? = null
    private var gbDevice: GBDevice? = null
    private var selectedItems: BitSet? = null
    private lateinit var swipeLayout: SwipeRefreshLayout
    private var summariesAdapter: WorkoutSummariesAdapter? = null
    private var actionMode: ActionMode? = null

    private val activityDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == WorkoutDetailsActivity.RESULT_WORKOUT_CHANGED) {
            // FIXME we could try to only update the workouts that changed
            refresh()
        }
    }

    private val activityFilterLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.extras?.let { bundle ->
            activityFilter = bundle.getInt("activityFilter", 0)
            dateFromFilter = bundle.getLong("dateFromFilter", 0)
            dateToFilter = bundle.getLong("dateToFilter", 0)
            deviceFilter = bundle.getLong("deviceFilter", 0)
            nameContainsFilter = bundle.getString("nameContainsFilter")
            itemsFilter = bundle.getSerializableCompat<ArrayList<Long>>("itemsFilter")
            refresh()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBDevice.ACTION_DEVICE_CHANGED -> {
                    val device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
                    if (device == null) {
                        LOG.error("Got device changed without device")
                        return
                    }
                    if (device != gbDevice) {
                        return
                    }
                    if (device.isBusy) {
                        swipeLayout.isRefreshing = true
                    } else {
                        val wasBusy = swipeLayout.isRefreshing
                        swipeLayout.isRefreshing = false
                        if (wasBusy) {
                            refresh()
                        }
                    }
                }
                // ExploreSync (and other long-running fetches) hold the
                // device busy for the whole catalog walk, so the
                // busy→idle edge above only fires at session end.
                // Refresh on every per-line flush as well so newly
                // imported activities appear in the list right away.
                // Pass silent=true so the dashboard's loading shimmer
                // and swipe-refresh spinner don't blink on every
                // incremental update.
                GBApplication.ACTION_NEW_DATA -> refresh(silent = true)
                HealthConnectWorkoutSync.ACTION_STATE_CHANGED -> refresh(silent = true)
                else -> LOG.warn("Got unexpected action {}", intent.action)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.activity_action_manage_timestamp -> {
                resetFetchTimestampToChosenDate()
                true
            }
            R.id.activity_action_filter -> {
                runFilterActivity()
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        gbDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
            ?: throw IllegalArgumentException("Must provide a device when invoking this activity")
        deviceFilter = getDeviceId(gbDevice!!)

        // Load and apply saved quick filter
        applySavedQuickFilter()

        val filterLocal = IntentFilter().apply {
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
            addAction(GBApplication.ACTION_NEW_DATA)
            addAction(HealthConnectWorkoutSync.ACTION_STATE_CHANGED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filterLocal)

        super.onCreate(savedInstanceState)

        setupViews()
        setupViewModel()
    }

    private fun setupViews() {
        val workoutSummariesAdapter = WorkoutSummariesAdapter(
            this,
            gbDevice!!,
            activityFilter,
            dateFromFilter,
            dateToFilter,
            nameContainsFilter,
            deviceFilter,
            itemsFilter
        )
        selectedItems = workoutSummariesAdapter.selectedItems

        workoutSummariesAdapter.setOnItemClickListener { position ->
            if (!selectedItems!!.isEmpty) {
                selectedItems!!.set(position, !selectedItems!!.get(position))
                workoutSummariesAdapter.notifyItemChanged(position)
                if (!selectedItems!!.isEmpty) {
                    startActionMode()
                } else {
                    stopActionMode()
                }
                return@setOnItemClickListener
            }
            if (position == 0) return@setOnItemClickListener // item 0 is empty for dashboard
            val summary = workoutSummariesAdapter.getItem(position)
            if (summary != null) {
                try {
                    showActivityDetail(position - 1)
                } catch (e: Exception) {
                    GB.toast(
                        applicationContext,
                        "Unable to display Activity Detail: ${e.localizedMessage}",
                        Toast.LENGTH_LONG,
                        GB.ERROR,
                        e
                    )
                }
            }
        }

        workoutSummariesAdapter.setOnItemLongClickListener { position ->
            selectedItems!!.set(position, !selectedItems!!.get(position))
            workoutSummariesAdapter.notifyItemChanged(position)

            if (!selectedItems!!.isEmpty) {
                startActionMode()
            } else {
                stopActionMode()
            }
        }

        setItemAdapter(workoutSummariesAdapter)
        summariesAdapter = workoutSummariesAdapter

        // The dashboard row gets a notifyItemChanged on every silent
        // refresh during sync; the default ChangeAnimator cross-fades
        // that, which reads as a distracting opacity blink. Disable
        // just the change animation — add/remove animations still run.
        val recycler = findViewById<RecyclerView>(R.id.itemListView)
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        swipeLayout = findViewById(R.id.list_activity_swipe_layout)
        swipeLayout.setOnRefreshListener {
            if (GBApplication.getPrefs().refreshOnSwipe()) {
                fetchTrackData()
            } else {
                swipeLayout.isRefreshing = false
            }
        }
        swipeLayout.setEnabled(GBApplication.getPrefs().refreshOnSwipe())

        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { fetchTrackData() }
        // The FAB comes from the layout shared by every list screen, so it is only recolored here.
        fab.applyAccentColors()
    }

    private fun setupViewModel() {
        viewModel.summaries.observe(this) { summaries ->
            // Set before the items, so the first bind of every row already has its indicator.
            summariesAdapter?.uploadStatuses = viewModel.uploadStatuses.value.orEmpty()
            summariesAdapter?.healthConnectFailures = viewModel.healthConnectFailures.value.orEmpty()
            itemAdapter?.setItems(summaries, true)
            activityKindMap = fillKindMap()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            swipeLayout.isRefreshing = isLoading
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                GB.toast(this, it, Toast.LENGTH_LONG, GB.ERROR)
            }
        }

        viewModel.isDashboardLoading.observe(this) { isLoading ->
            (itemAdapter as WorkoutSummariesAdapter).isDashboardLoading = isLoading
            itemAdapter?.notifyItemChanged(0)
        }

        viewModel.dashboardStats.observe(this) { stats ->
            (itemAdapter as WorkoutSummariesAdapter).dashboardStats = stats
            itemAdapter?.notifyItemChanged(0)
        }

        refresh()
    }

    private fun stopActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private fun startActionMode() {
        val numSelected = selectedItems!!.cardinality()

        if (actionMode != null) {
            // already in action mode
            actionMode?.title = getString(R.string.number_selected_items, numSelected)
            return
        }

        actionMode = startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.title = getString(R.string.number_selected_items, numSelected)
                menuInflater.inflate(R.menu.activity_list_context_menu, menu)

                // For some reason the icons in the context menu are not tinted
                // by the theme, so we do it manually here
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(R.attr.actionmenu_icon_color, typedValue, true)
                val iconColor = typedValue.data
                for (i in 0 until menu.size) {
                    menu[i].icon?.setTint(iconColor)
                }

                findViewById<View>(R.id.fab).visibility = View.INVISIBLE
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.activity_action_delete -> {
                        val toDelete = ArrayList<BaseActivitySummary>()
                        for (i in 0 until selectedItems!!.length()) {
                            if (selectedItems!!.get(i)) {
                                itemAdapter?.getItem(i)?.let { toDelete.add(it) }
                            }
                        }

                        MaterialAlertDialogBuilder(this@WorkoutListActivity)
                            .setTitle(getString(R.string.sports_activity_confirm_delete_title, toDelete.size))
                            .setMessage(getString(R.string.sports_activity_confirm_delete_description, toDelete.size))
                            .setIcon(R.drawable.ic_delete_forever)
                            .setPositiveButton(R.string.yes) { _, _ -> deleteItems(toDelete) }
                            .setNegativeButton(R.string.no, null)
                            .show()

                        return true
                    }
                    R.id.activity_action_export -> {
                        val toExport = ArrayList<BaseActivitySummary>()
                        for (i in 0 until selectedItems!!.length()) {
                            if (selectedItems!!.get(i)) {
                                itemAdapter?.getItem(i)?.let { toExport.add(it) }
                            }
                        }
                        showExportDialog(toExport)
                        return true
                    }
                    R.id.activity_action_select_all -> {
                        for (i in 1 until itemAdapter!!.itemCount - 1) {
                            if (!selectedItems!!.get(i)) {
                                selectedItems!!.set(i, true)
                                itemAdapter!!.notifyItemChanged(i)
                            }
                        }
                        mode.title = getString(R.string.number_selected_items, selectedItems!!.cardinality())
                        return true
                    }
                    R.id.activity_action_addto_filter -> {
                        val toFilter = ArrayList<Long>()
                        for (i in 0 until selectedItems!!.length()) {
                            if (selectedItems!!.get(i)) {
                                itemAdapter?.getItem(i)?.id?.let { id ->
                                    toFilter.add(id)
                                }
                            }
                        }
                        itemsFilter = toFilter
                        refresh()
                        return true
                    }
                }
                mode.finish()
                actionMode = null
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                for (i in 0 until selectedItems!!.length()) {
                    if (selectedItems!!.get(i)) {
                        selectedItems!!.set(i, false)
                        itemAdapter?.notifyItemChanged(i)
                    }
                }
                findViewById<View>(R.id.fab).visibility = View.VISIBLE
            }
        })
    }

    private fun fillKindMap(): HashMap<String, ActivityKind> {
        val newMap = HashMap<String, ActivityKind>(0)
        newMap[getString(R.string.activity_summaries_all_activities)] = ActivityKind.UNKNOWN

        itemAdapter?.items?.forEach { item ->
            val activityName = ActivityKind.fromCode(item.activityKind).getLabel(this)
            if (!newMap.containsKey(activityName) && item.activityKind != 0) {
                newMap[activityName] = ActivityKind.fromCode(item.activityKind)
            }
        }
        return newMap
    }

    private fun resetFetchTimestampToChosenDate() {
        val currentDate = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    set(year, monthOfYear, dayOfMonth)
                }
                val timestamp = date.timeInMillis - 1000
                GBApplication.getDeviceSpecificSharedPrefs(gbDevice!!.address).edit().apply {
                    remove("lastSportsActivityTimeMillis")
                    putLong("lastSportsActivityTimeMillis", timestamp)
                    apply()
                }
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DATE)
        ).show()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun deleteItems(items: List<BaseActivitySummary>) {
        items.forEach { item ->
            try {
                item.delete()
            } catch (_: Exception) {
                // pass delete error
            }
        }
        refresh()
    }

    private fun fetchTrackData() {
        gbDevice?.let { device ->
            if (device.isInitialized && !device.isBusy) {
                swipeLayout.isRefreshing = true
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_GPS_TRACKS)
            } else {
                swipeLayout.isRefreshing = false
                if (!device.isInitialized) {
                    GB.toast(this, getString(R.string.device_not_connected), Toast.LENGTH_SHORT, GB.ERROR)
                }
            }
        }
    }

    private fun showExportDialog(summaries: List<BaseActivitySummary>) {
        val device = gbDevice ?: return
        if (summaries.isEmpty()) return

        val prefs = GBApplication.getPrefs()
        val view = layoutInflater.inflate(R.layout.dialog_workout_export, null)
        val boxes = mapOf(
            WorkoutBatchExporter.Part.FIT to view.findViewById<CheckBox>(R.id.workout_export_fit),
            WorkoutBatchExporter.Part.GPX to view.findViewById<CheckBox>(R.id.workout_export_gpx),
            WorkoutBatchExporter.Part.RAW to view.findViewById<CheckBox>(R.id.workout_export_raw)
        )
        fun prefKey(part: WorkoutBatchExporter.Part) = "workout_export_" + part.name.lowercase(Locale.ROOT)
        boxes.forEach { (part, box) ->
            box.isChecked = prefs.getBoolean(prefKey(part), part != WorkoutBatchExporter.Part.RAW)
        }
        val shareAs = view.findViewById<RadioGroup>(R.id.workout_export_share_as)
        shareAs.check(
            if (prefs.getBoolean(PREF_EXPORT_AS_ZIP, false)) R.id.workout_export_zip else R.id.workout_export_separate
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_export_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val asZip = shareAs.checkedRadioButtonId == R.id.workout_export_zip
                prefs.preferences.edit().apply {
                    boxes.forEach { (part, box) -> putBoolean(prefKey(part), box.isChecked) }
                    putBoolean(PREF_EXPORT_AS_ZIP, asZip)
                    apply()
                }
                val parts = boxes.filterValues { it.isChecked }.keys
                exportWorkouts(device, summaries, parts, asZip)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = boxes.values.any { it.isChecked }
        boxes.values.forEach { box ->
            box.setOnCheckedChangeListener { _, _ -> ok.isEnabled = boxes.values.any { it.isChecked } }
        }
    }

    private fun exportWorkouts(
        device: GBDevice,
        summaries: List<BaseActivitySummary>,
        parts: Set<WorkoutBatchExporter.Part>,
        asZip: Boolean
    ) {
        actionMode?.finish()
        GB.toast(this, getString(R.string.workout_export_preparing), Toast.LENGTH_SHORT, GB.INFO)
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    WorkoutBatchExporter.export(this@WorkoutListActivity, device, summaries, parts, asZip)
                }
            } catch (e: Exception) {
                LOG.error("Failed to export workouts", e)
                GB.toast(this@WorkoutListActivity, getString(R.string.workout_export_nothing), Toast.LENGTH_LONG, GB.ERROR)
                return@launch
            }
            if (result.files.isEmpty()) {
                GB.toast(this@WorkoutListActivity, getString(R.string.workout_export_nothing), Toast.LENGTH_LONG, GB.ERROR)
                return@launch
            }
            if (result.skipped > 0) {
                GB.toast(
                    this@WorkoutListActivity,
                    resources.getQuantityString(R.plurals.workout_export_skipped, result.skipped, result.skipped),
                    Toast.LENGTH_LONG,
                    GB.WARN
                )
            }
            AndroidUtils.shareFiles(this@WorkoutListActivity, result.files, WorkoutBatchExporter.mimeTypeFor(result.files))
        }
    }

    private fun showActivityDetail(position: Int) {
        val intent = WorkoutDetailsActivity.createFilteredListIntent(
            this,
            gbDevice!!,
            position,
            activityFilter,
            dateFromFilter,
            dateToFilter,
            nameContainsFilter,
            deviceFilter,
            itemsFilter
        )
        activityDetailLauncher.launch(intent)
    }

    private fun runFilterActivity() {
        val filterIntent = Intent(this, ActivitySummariesFilter::class.java).apply {
            putExtra("activityKindMap", activityKindMap)
            putExtra("itemsFilter", ArrayList(itemsFilter ?: emptyList()))
            putExtra("activityFilter", activityFilter)
            putExtra("dateFromFilter", dateFromFilter)
            putExtra("dateToFilter", dateToFilter)
            putExtra("deviceFilter", deviceFilter)
            putExtra("initial_deviceFilter", getDeviceId(gbDevice!!))
            putExtra("nameContainsFilter", nameContainsFilter)
        }
        activityFilterLauncher.launch(filterIntent)
    }

    private fun getDeviceId(device: GBDevice): Long {
        return try {
            GBApplication.acquireDbReadOnly().use { handler ->
                DBHelper.findDevice(device, handler.daoSession)?.id ?: 0L
            }
        } catch (e: Exception) {
            LOG.error("Failed to get device id", e)
            0L
        }
    }

    override fun refresh() {
        refresh(silent = false)
    }

    private fun refresh(silent: Boolean) {
        // The adapter only reads the kind and date filters to title the dashboard, so it has to
        // be kept in sync here: the filter screen result only updates this activity's fields.
        summariesAdapter?.let { adapter ->
            adapter.setActivityKindFilter(activityFilter)
            adapter.setDateFromFilter(dateFromFilter)
            adapter.setDateToFilter(dateToFilter)
            adapter.setNameContainsFilter(nameContainsFilter)
            adapter.setItemsFilter(itemsFilter)
            adapter.setDeviceFilter(deviceFilter)
        }
        gbDevice?.let { device ->
            viewModel.loadSummaries(
                device,
                activityFilter,
                dateFromFilter,
                dateToFilter,
                nameContainsFilter,
                deviceFilter,
                itemsFilter,
                silent
            )
        }
    }

    private fun applySavedQuickFilter() {
        val savedFilter = GBApplication.getPrefs().preferences.getString("workout_list_quick_filter", "noselection")
        val dateRange = WorkoutFilterUtils.getDateRangeForFilter(savedFilter) ?: return

        dateFromFilter = dateRange.first
        dateToFilter = dateRange.second
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutListActivity::class.java)
        private const val PREF_EXPORT_AS_ZIP = "workout_export_as_zip"
    }
}
