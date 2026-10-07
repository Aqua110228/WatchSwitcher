/*  Copyright (C) 2020-2025 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.EndurainApiClient
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.EndurainSetupViewModel
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.WandererApiClient
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.WandererTokenManager
import nodomain.freeyourgadget.gadgetbridge.activities.fit.FitViewerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.DefaultWorkoutCharts
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWorkoutDetailsBinding
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.Device
import nodomain.freeyourgadget.gadgetbridge.export.FitExporter
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutViewModel
import nodomain.freeyourgadget.gadgetbridge.util.ActivitySummaryUtils
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.BarShade
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

class WorkoutDetailsFragment : Fragment(), MenuProvider {
    private var workoutId: Long = -1
    private var currentWorkout: Workout? = null
    private var rawDetailsFile: File? = null
    private var rawGpsFile: File? = null
    private lateinit var gbDevice: GBDevice

    private lateinit var binding: FragmentWorkoutDetailsBinding

    private lateinit var tabsPagerAdapter: WorkoutTabsPagerAdapter

    private lateinit var workoutEditor: WorkoutEditor

    private val workoutValueFormatter = WorkoutValueFormatter()

    private var menu: Menu? = null

    private lateinit var workoutViewModel: WorkoutViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workoutEditor = WorkoutEditor(requireContext(), this)
        workoutViewModel = ViewModelProvider(requireActivity()).get(WorkoutViewModel::class.java)
        arguments?.let {
            workoutId = it.getLong(ARG_WORKOUT_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWorkoutDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (BarShade.continuesToolbar(requireContext(), R.attr.tab_bar_bg)) {
            (requireActivity() as AbstractGBActivity).setTopShade(binding.tabRowShade)
        }

        // Attach workout tabs.
        val viewPager = binding.tabsViewPager
        val tabLayout = binding.tabLayout
        tabsPagerAdapter = WorkoutTabsPagerAdapter(childFragmentManager, lifecycle, workoutId)
        viewPager.adapter = tabsPagerAdapter
        // Keep every tab's fragment (and its view) alive once created instead of the ViewPager2
        // default, which tears a tab's view down as soon as it's swiped away and rebuilds it
        // from scratch next time — expensive for Charts, which builds a fresh chart view per
        // metric. There are only a handful of tabs, so keeping them all resident is cheap.
        viewPager.offscreenPageLimit = WorkoutTab.entries.size
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(tabsPagerAdapter.titleResAt(position))
        }.attach()

        loadWorkoutData()
    }

    override fun onResume() {
        super.onResume()

        updateActionBarTitle()
    }

    private fun updateActionBarTitle() {
        workoutLabel()?.let {
            (activity as? AppCompatActivity)?.supportActionBar?.title = it
        }
    }

    /** The workout's custom label, falling back to its sport/activity kind name. */
    private fun workoutLabel(): String? {
        val summary = currentWorkout?.summary ?: return null
        return summary.name?.takeIf { it.isNotBlank() }
            ?: summary.activityKind.let { ActivityKind.fromCode(it).getLabel(requireContext()) }
    }

    private fun loadWorkoutData() {
        if (workoutId == -1L) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                currentWorkout = withContext(Dispatchers.IO) {
                    val summary = GBApplication.acquireDbReadOnly().use { dbHandler ->
                        dbHandler.daoSession.baseActivitySummaryDao.load(workoutId)
                    }
                    gbDevice = getGBDevice(summary.device)
                    workoutEditor.gbDevice = gbDevice
                    rawDetailsFile = gbDevice.deviceCoordinator.getWorkoutRawDetailsFile(gbDevice, summary)
                    rawGpsFile = gbDevice.deviceCoordinator.getWorkoutRawGpsFile(gbDevice, summary)
                    val parsedWorkout = try {
                        gbDevice.deviceCoordinator.getActivitySummaryParser(gbDevice, requireContext())
                            .parseWorkout(summary, true)
                    } catch (e: Exception) {
                        // Do not break completely - use any previously processed data
                        GB.toast(requireContext(), "Error while loading workout", Toast.LENGTH_SHORT, GB.ERROR, e)
                        Workout(
                            summary,
                            ActivitySummaryData.fromJson(summary.summaryData),
                            mutableListOf()
                        )
                    }
                    if (parsedWorkout.charts.isEmpty()) {
                        try {
                            val activityTrackProvider =
                                gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, requireContext())
                            if (activityTrackProvider != null) {
                                val activityPoints = activityTrackProvider.getActivityTrack(parsedWorkout.summary)?.allPoints
                                if (!activityPoints.isNullOrEmpty()) {
                                    val defaultCharts = DefaultWorkoutCharts.buildDefaultCharts(
                                        requireContext(),
                                        activityPoints,
                                        ActivityKind.fromCode(parsedWorkout.summary.activityKind),
                                        parsedWorkout.data,
                                        gbDevice
                                    )
                                    return@withContext Workout(parsedWorkout.summary, parsedWorkout.data, defaultCharts)
                                }
                            }
                        } catch (e: Exception) {
                            LOG.error("Failed to build default charts", e)
                        }
                    }
                    return@withContext parsedWorkout
                }

                requireActivity().addMenuProvider(
                    this@WorkoutDetailsFragment,
                    viewLifecycleOwner,
                    Lifecycle.State.RESUMED
                )

                currentWorkout?.let { workout ->
                    workoutValueFormatter.setActivityKind(ActivityKind.fromCode(workout.summary.activityKind))
                    updateActionBarTitle()
                    workoutViewModel.setWorkout(workout, workoutId)
                    tabsPagerAdapter.setOptionalTabs(hasCharts(workout), hasLaps(workout))

                    showLoading(false)
                } ?: run {
                    showError("Workout not found")
                }
            } catch (e: Exception) {
                LOG.error("Error loading workout data", e)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                showError("Failed to load workout: $sw")
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.tabLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.tabsViewPager.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.errorMessage.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingSpinner.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.tabsViewPager.visibility = View.GONE
        binding.errorMessage.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }

    private fun hasCharts(workout: Workout): Boolean {
        return workout.charts.isNotEmpty() || gbDevice.deviceCoordinator.supportsHeartRateMeasurement(gbDevice)
    }

    /** Whether the workout has laps/intervals data (cardio-type workouts), and so gets a Laps tab. */
    private fun hasLaps(workout: Workout): Boolean {
        val groups = ActivitySummaryGroup.buildGroupedList(workout.data)
        return !groups[ActivitySummaryEntries.GROUP_LAPS].isNullOrEmpty() ||
            !groups[ActivitySummaryEntries.GROUP_INTERVALS].isNullOrEmpty()
    }

    private fun workoutHasGps(workout: Workout): Boolean {
        if (workout.data.hasGps()) {
            return true
        }

        workout.summary.gpxTrack?.let { gpxTrack ->
            val existing = FileUtils.tryFixPath(File(gpxTrack))
            if (existing != null && existing.canRead()) {
                return true
            }
        }

        return false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menu.clear()
        menuInflater.inflate(R.menu.activity_take_screenshot_menu, menu)
        this.menu = menu
        updateMenuItems(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val workout = currentWorkout ?: return false

        return when (menuItem.itemId) {
            R.id.activity_action_take_screenshot -> {
                takeSharedScreenshot()
                true
            }

            R.id.activity_action_show_gpx -> {
                viewGpxTrack()
                true
            }

            R.id.activity_action_share_gpx -> {
                shareGpxTrack()
                true
            }

            R.id.activity_action_upload_to_endurain -> {
                uploadToEndurain()
                true
            }

            R.id.activity_action_upload_to_wanderer -> {
                uploadToWanderer()
                true
            }

            R.id.activity_action_dev_inspect_file -> {
                val rawDetailsPath = workout.summary.rawDetailsPath ?: return true
                val intent = Intent(requireContext(), FitViewerActivity::class.java).apply {
                    putExtra(FitViewerActivity.EXTRA_PATH, File(rawDetailsPath).absolutePath)
                }
                startActivity(intent)
                true
            }

            R.id.activity_action_dev_share_raw_summary -> {
                shareRawSummary(workout)
                true
            }

            R.id.activity_action_dev_share_raw_details -> {
                shareRawFile(rawDetailsFile, "details")
                true
            }

            R.id.activity_action_dev_share_raw_gps -> {
                shareRawFile(rawGpsFile, "GPS")
                true
            }

            R.id.activity_action_dev_share_json_details -> {
                shareJsonDetails(workout)
                true
            }

            R.id.activity_action_share_fit -> {
                exportFit(workout)
                true
            }

            R.id.activity_action_upload_status -> {
                showUploadStatus(workout.summary)
                true
            }

            R.id.activity_summary_detail_action_edit_name -> {
                currentWorkout?.let {
                    workoutEditor.editWorkoutName(it, object : WorkoutEditor.Callback {
                        override fun onWorkoutUpdated() {
                            notifyWorkoutChanged()
                            updateActionBarTitle()
                            workoutViewModel.refreshWorkout(workoutId)
                        }
                    })
                }
                true
            }

            R.id.activity_summary_detail_action_edit_type -> {
                currentWorkout?.let {
                    workoutEditor.editWorkoutType(it, object : WorkoutEditor.Callback {
                        override fun onWorkoutUpdated() {
                            notifyWorkoutChanged()
                            workoutViewModel.refreshWorkout(workoutId)
                            scheduleUploadSync()
                        }
                    })
                }
                true
            }

            R.id.activity_summary_detail_action_add_photo -> {
                currentWorkout?.let {
                    workoutEditor.setHeaderPhoto(it, object : WorkoutEditor.Callback {
                        override fun onWorkoutUpdated() {
                            notifyWorkoutChanged()
                            workoutViewModel.refreshWorkout(workoutId)
                            // Swap which of add/remove-photo is visible in the overflow menu.
                            requireActivity().invalidateMenu()
                            scheduleUploadSync()
                        }
                    })
                }
                true
            }

            R.id.activity_summary_detail_action_remove_photo -> {
                currentWorkout?.let {
                    workoutEditor.removeHeaderPhoto(it, object : WorkoutEditor.Callback {
                        override fun onWorkoutUpdated() {
                            notifyWorkoutChanged()
                            workoutViewModel.refreshWorkout(workoutId)
                            // Swap which of add/remove-photo is visible in the overflow menu.
                            requireActivity().invalidateMenu()
                            scheduleUploadSync()
                        }
                    })
                }
                true
            }

            R.id.activity_summary_detail_action_edit_gps -> {
                currentWorkout?.let {
                    workoutEditor.editGpsTrack(it, object : WorkoutEditor.Callback {
                        override fun onWorkoutUpdated() {
                            notifyWorkoutChanged()
                            // Reload the entire workout data so that we can refresh the charts
                            loadWorkoutData()
                            scheduleUploadSync()
                        }
                    })
                }
                true
            }

            android.R.id.home -> {
                requireActivity().finish()
                true
            }

            else -> false
        }
    }

    /**
     * Pushes an edit made here to the online fitness trackers without waiting for the next data
     * sync. The worker does nothing when auto-upload is off or the workout was never uploaded.
     */
    private fun scheduleUploadSync() {
        WorkoutUploadWorker.enqueue(requireContext(), gbDevice.address)
    }

    private fun notifyWorkoutChanged() {
        val resultIntent = Intent().apply {
            putExtra(ARG_WORKOUT_ID, workoutId)
        }
        requireActivity().setResult(WorkoutDetailsActivity.RESULT_WORKOUT_CHANGED, resultIntent)
    }

    private fun updateMenuItems(menu: Menu) {
        val workout = currentWorkout ?: return

        val hasGpx = workoutHasGps(workout)
        val hasRawSummary = workout.summary.rawSummaryData != null
        val hasRawDetailsPath = workout.summary.rawDetailsPath?.let { FileUtils.tryFixPath(File(it)) != null } ?: false

        val overflowMenu = menu.findItem(R.id.activity_detail_overflowMenu)?.subMenu
        if (overflowMenu != null) {
            overflowMenu.findItem(R.id.activity_action_show_gpx)?.isVisible = hasGpx
            overflowMenu.findItem(R.id.activity_action_share_gpx)?.isVisible = hasGpx
            overflowMenu.findItem(R.id.activity_action_dev_inspect_file)?.isVisible =
                hasRawDetailsPath && workout.summary.rawDetailsPath?.lowercase(Locale.ROOT)?.endsWith(".fit") == true
            overflowMenu.findItem(R.id.activity_action_dev_share_raw_summary)?.isVisible = hasRawSummary
            overflowMenu.findItem(R.id.activity_action_dev_share_raw_details)?.isVisible = rawDetailsFile != null
            overflowMenu.findItem(R.id.activity_action_dev_share_raw_gps)?.isVisible = rawGpsFile != null

            val devToolsMenu = overflowMenu.findItem(R.id.activity_action_dev_tools)
            val devToolsSubMenu = devToolsMenu?.subMenu
            devToolsMenu?.isVisible = devToolsSubMenu != null && devToolsSubMenu.hasVisibleItems()
        }

        val overflowMenu2 = menu.findItem(R.id.activity_detail_overflowMenu2)?.subMenu
        overflowMenu2?.findItem(R.id.activity_summary_detail_action_add_photo)?.isVisible = workout.summary.headerPhoto == null
        overflowMenu2?.findItem(R.id.activity_summary_detail_action_remove_photo)?.isVisible = workout.summary.headerPhoto != null

        // Endurain accepts FIT (built from the summary alone if needed), so it is offered
        // for any workout. Wanderer only supports GPX uploads, so it requires a GPS track.
        val endurainVm: EndurainSetupViewModel by viewModels()
        val endurainServer = GBApplication.getPrefs().preferences
            .getString(WorkoutUploader.PREF_ENDURAIN_SERVER, null)
        val wandererServer = GBApplication.getPrefs().preferences
            .getString(WorkoutUploader.PREF_WANDERER_SERVER, null)
        viewLifecycleOwner.lifecycleScope.launch {
            val enduranVisible = withContext(Dispatchers.IO) {
                endurainServer != null && endurainVm.endurainTokenManager.isLoggedIn()
            }
            val wandererVisible = withContext(Dispatchers.IO) {
                hasGpx && wandererServer != null && WandererTokenManager(requireContext()).isLoggedIn()
            }
            overflowMenu?.findItem(R.id.activity_action_upload_to_endurain)?.isVisible = enduranVisible
            overflowMenu?.findItem(R.id.activity_action_upload_to_wanderer)?.isVisible = wandererVisible
            overflowMenu?.findItem(R.id.activity_action_upload_status)?.isVisible = enduranVisible || wandererVisible

        }

    }

    /**
     * Shows where [summary] stands with each online fitness tracker, which the single indicator
     * on the workout list cannot express. Every configured service gets a line, including the
     * ones that have not taken the workout, so the dialog also answers "why is this not up there".
     *
     * Also called by [WorkoutTabOverviewFragment] when its header's upload indicator is tapped.
     */
    internal fun showUploadStatus(summary: BaseActivitySummary) {
        val context = requireContext()
        val rows = WorkoutUploadStore.rowsForSummaries(listOfNotNull(summary.id))
            .values.firstOrNull().orEmpty()
        val prefs = GBApplication.getPrefs()
        val hasTrack = WorkoutUploader.summaryHasTrack(summary)
        val sourceHash = WorkoutUploadStore.sourceHashOf(summary)

        val view = layoutInflater.inflate(R.layout.dialog_workout_upload_status, null)
        val container = view.findViewById<LinearLayout>(R.id.upload_status_container)

        WorkoutUploadTargets.ALL
            .filter { it.isLoggedIn(context) }
            .forEach { target ->
                val row = rows[target.service]
                val state = when {
                    row?.lastError != null -> row.lastError
                    row?.status == WorkoutUploadStore.STATUS_FAILED ->
                        context.getString(R.string.workout_upload_status_failed)

                    row?.status == WorkoutUploadStore.STATUS_SUCCESS && row.sourceHash == sourceHash ->
                        context.getString(R.string.workout_upload_status_uploaded)

                    row?.status == WorkoutUploadStore.STATUS_SUCCESS ->
                        context.getString(R.string.workout_upload_status_pending)

                    target.requiresTrack && !hasTrack ->
                        context.getString(R.string.workout_upload_status_no_track)

                    !target.isAutoUploadEnabled(prefs) ->
                        context.getString(R.string.workout_upload_status_disabled)

                    else -> context.getString(R.string.workout_upload_status_not_uploaded)
                }

                val item = layoutInflater.inflate(R.layout.item_workout_upload_status, container, false)
                item.findViewById<TextView>(R.id.upload_status_service).setText(target.nameRes)
                val stateView = item.findViewById<TextView>(R.id.upload_status_state)
                stateView.text = state
                bindRemoteActivity(
                    item.findViewById(R.id.upload_status_link),
                    stateView,
                    target,
                    row?.remoteActivityId
                )
                container.addView(item)
            }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.workout_upload_status_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Shows which activity a service holds, as a link to it when the service is set up well enough
     * for one to be built, and as plain text otherwise so the id is still there to search on.
     */
    private fun bindRemoteActivity(
        linkView: TextView,
        stateView: TextView,
        target: WorkoutUploadTarget,
        remoteActivityId: String?
    ) {
        if (remoteActivityId == null) {
            linkView.visibility = View.GONE
            return
        }
        val url = target.activityUrl(requireContext(), remoteActivityId)
        if (url == null) {
            // Nothing to open, so show the id itself: it is all the user has to find the activity
            // with, and it reads as text rather than as a link.
            linkView.text = getString(R.string.workout_upload_status_remote_id, remoteActivityId)
            linkView.setTextColor(stateView.currentTextColor)
            linkView.background = null
            linkView.setOnClickListener(null)
            linkView.isClickable = false
            return
        }
        linkView.setText(R.string.workout_upload_status_open)
        linkView.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                LOG.warn("Unable to open {}", url, e)
                GB.toast(
                    requireContext(),
                    getString(R.string.endurain_failed_to_open_browser, e.message),
                    Toast.LENGTH_LONG,
                    GB.WARN
                )
            }
        }
    }

    /**
     * Screenshots whichever tab is currently visible (each tab fragment exposes its own root via
     * [WorkoutTabScreenshotProvider], since the host no longer owns a single scrollable view now
     * that Overview/Charts/Laps/Details are split into tabs).
     */
    private fun takeSharedScreenshot() {
        lifecycleScope.launch {
            try {
                val workout = currentWorkout ?: return@launch
                val screenshotView = (tabsPagerAdapter.fragmentAt(binding.tabsViewPager.currentItem)
                        as? WorkoutTabScreenshotProvider)?.screenshotView ?: return@launch

                // Capture the full scrollable content, not just what's currently visible on screen.
                val content = (screenshotView as? ViewGroup)?.getChildAt(0) ?: screenshotView
                val width = content.width
                val height = content.height
                if (width <= 0 || height <= 0) return@launch

                val bitmap = createBitmap(width, height)
                val canvas = Canvas(bitmap)
                canvas.drawColor(GBApplication.getWindowBackgroundColor(requireContext()))
                screenshotView.draw(canvas)

                val fileName = FileUtils.makeValidFileName(
                    "Screenshot-${
                        ActivityKind.fromCode(workout.summary.activityKind).getLabel(requireContext()).lowercase()
                    }-${DateTimeUtils.formatIso8601(workout.summary.startTime)}.png"
                )
                val targetFile = File(FileUtils.getExternalFilesDir(), fileName)
                withContext(Dispatchers.IO) {
                    FileOutputStream(targetFile).use { fOut ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut)
                    }
                }

                shareScreenshot(targetFile)
                GB.toast(requireContext(), "Screenshot saved", Toast.LENGTH_LONG, GB.INFO)
            } catch (e: IOException) {
                LOG.error("Error taking screenshot", e)
            }
        }
    }

    private fun shareScreenshot(targetFile: File) {
        val subject = currentWorkout?.summary?.name?.let { "Sports Activity" }
        val contentUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.screenshot_provider",
            targetFile
        )
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            putExtra(Intent.EXTRA_STREAM, contentUri)
        }

        try {
            startActivity(Intent.createChooser(sharingIntent, "Share via"))
        } catch (e: Exception) {
            LOG.error("Failed to share screenshot", e)
            Toast.makeText(requireContext(), R.string.activity_error_no_app_for_png, Toast.LENGTH_LONG).show()
        }
    }

    private fun viewGpxTrack() {
        val workout = currentWorkout ?: return
        val activityTrackProvider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, requireContext())
        val gpxFile = ActivitySummaryUtils.getShareableGpxFile(activityTrackProvider, workout.summary)

        if (gpxFile == null) {
            GB.toast(requireContext(),
                getString(R.string.no_gpx_track_in_activity_toast), Toast.LENGTH_LONG, GB.INFO)
            return
        }

        try {
            AndroidUtils.viewFile(gpxFile.path, "application/gpx+xml", requireContext())
        } catch (e: Exception) {
            GB.toast(
                requireContext(),
                getString(R.string.unable_to_display_gpx_track_toast, e.localizedMessage),
                Toast.LENGTH_LONG,
                GB.ERROR,
                e
            )
        }
    }

    private fun shareGpxTrack() {
        val workout = currentWorkout ?: return
        val activityTrackProvider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, requireContext())
        val gpxFile = ActivitySummaryUtils.getShareableGpxFile(activityTrackProvider, workout.summary)

        if (gpxFile == null) {
            GB.toast(requireContext(), getString(R.string.no_gpx_track_in_activity_toast), Toast.LENGTH_LONG, GB.INFO)
            return
        }

        try {
            AndroidUtils.shareFile(requireContext(), gpxFile, "application/gpx+xml")
        } catch (e: Exception) {
            GB.toast(
                requireContext(),
                getString(R.string.unable_to_share_gpx_track_toast, e.localizedMessage),
                Toast.LENGTH_LONG,
                GB.ERROR,
                e
            )
        }
    }

    private fun uploadToEndurain() {
        val workout = currentWorkout ?: return

        lifecycleScope.launch {
            val activityFile = try {
                buildFitFile(workout)
            } catch (e: Exception) {
                LOG.error("Failed to build FIT for Endurain upload", e)
                GB.toast(
                    getString(R.string.endurain_unable_to_upload_gpx_file_toast, e.localizedMessage),
                    Toast.LENGTH_LONG,
                    GB.ERROR,
                    e
                )
                return@launch
            }

            WorkoutUploader.uploadToEndurain(requireContext(), workout.summary, activityFile) { result ->
                val summaryId = workout.summary.id
                if (result.success && summaryId != null) {
                    // See WorkoutUploadWorker: a photo the service did not take leaves both
                    // fingerprints unset so the next sync comes back for it.
                    val photoLanded =
                        workout.summary.headerPhoto == null || result.photoMediaId != null
                    WorkoutUploadStore.recordSuccess(
                        summaryId, WorkoutUploadStore.SERVICE_ENDURAIN, result.remoteActivityId,
                        if (photoLanded) {
                            WorkoutUploadStore.photoHashOf(workout.summary.headerPhoto)
                        } else {
                            null
                        },
                        result.photoMediaId,
                        if (photoLanded) WorkoutUploadStore.sourceHashOf(workout.summary) else null,
                        WorkoutUploadStore.fileHashOf(activityFile),
                        WorkoutUploader.summaryHasTrack(workout.summary)
                    )
                } else if (summaryId != null) {
                    WorkoutUploadStore.recordFailure(
                        summaryId, WorkoutUploadStore.SERVICE_ENDURAIN, result.reason
                    )
                }
                activity?.runOnUiThread {
                    // The workout list shows the upload state per row, so it has to reload.
                    if (isAdded) {
                        notifyWorkoutChanged()
                        workoutViewModel.refreshWorkout(workoutId)
                    }
                    if (result.success)
                        GB.toast(
                            getString(R.string.endurain_successfully_uploaded_toast),
                            Toast.LENGTH_SHORT,
                            GB.INFO
                        )
                    else
                        GB.toast(
                            result.reason ?: getString(R.string.endurain_error_while_uploading_toast),
                            Toast.LENGTH_LONG,
                            GB.ERROR
                        )
                }
            }
        }
    }

    private fun uploadToWanderer() {
        val workout = currentWorkout ?: return
        val activityTrackProvider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, requireContext())

        // Wanderer only supports GPX uploads, so always send GPX (never a FIT file).
        val activityFile = ActivitySummaryUtils.getShareableGpxFile(activityTrackProvider, workout.summary)
        if (activityFile == null) {
            GB.toast(getString(R.string.no_activity_track_in_activity_toast), Toast.LENGTH_LONG, GB.INFO)
            return
        }

        WorkoutUploader.uploadToWanderer(requireContext(), activityFile) { result ->
            val summaryId = workout.summary.id
            if (result.success && summaryId != null) {
                WorkoutUploadStore.recordSuccess(
                    summaryId, WorkoutUploadStore.SERVICE_WANDERER, result.remoteActivityId, null,
                    null,
                    WorkoutUploadStore.sourceHashOf(workout.summary),
                    WorkoutUploadStore.fileHashOf(activityFile),
                    true
                )
            } else if (summaryId != null) {
                WorkoutUploadStore.recordFailure(
                    summaryId, WorkoutUploadStore.SERVICE_WANDERER, result.reason
                )
            }
            activity?.runOnUiThread {
                if (isAdded) {
                    notifyWorkoutChanged()
                    workoutViewModel.refreshWorkout(workoutId)
                }
                if (result.success)
                    GB.toast(
                        getString(R.string.wanderer_toast_successfully_uploaded),
                        Toast.LENGTH_LONG,
                        GB.INFO
                    )
                else
                    GB.toast(
                        getString(R.string.wanderer_toast_upload_error, result.reason),
                        Toast.LENGTH_LONG,
                        GB.ERROR
                    )
            }
        }
    }

    private fun shareRawSummary(workout: Workout) {
        val rawSummaryData = workout.summary.rawSummaryData
        if (rawSummaryData == null) {
            GB.toast(requireContext(), "No raw summary in this activity", Toast.LENGTH_LONG, GB.WARN)
            return
        }

        val filename =
            FileUtils.makeValidFileName("${DateTimeUtils.formatIso8601(workout.summary.startTime)}_summary.bin")

        try {
            AndroidUtils.shareBytesAsFile(
                requireContext(),
                filename,
                rawSummaryData,
                "application/octet-stream"
            )
        } catch (e: Exception) {
            GB.toast(
                requireContext(),
                "Unable to share raw summary: ${e.localizedMessage}",
                Toast.LENGTH_LONG,
                GB.ERROR,
                e
            )
        }
    }

    private fun shareRawFile(file: File?, what: String) {
        if (file == null) {
            GB.toast(requireContext(), "No raw $what in this activity", Toast.LENGTH_LONG, GB.WARN)
            return
        }

        try {
            AndroidUtils.shareFile(requireContext(), file, "application/octet-stream")
        } catch (e: Exception) {
            GB.toast(
                requireContext(),
                "Unable to share raw $what: ${e.localizedMessage}",
                Toast.LENGTH_LONG,
                GB.ERROR,
                e
            )
        }
    }

    private fun shareJsonDetails(workout: Workout) {
        val filename = FileUtils.makeValidFileName("${DateTimeUtils.formatIso8601(workout.summary.startTime)}.json")

        try {
            AndroidUtils.shareBytesAsFile(
                requireContext(),
                filename,
                workout.data.toString().toByteArray(StandardCharsets.UTF_8),
                "application/json"
            )
        } catch (e: Exception) {
            GB.toast(
                requireContext(),
                "Unable to share json details: ${e.localizedMessage}",
                Toast.LENGTH_LONG,
                GB.ERROR,
                e
            )
        }
    }

    /**
     * Builds a FIT file for the given workout in the cache directory.
     *
     * FIT-native devices (Garmin, iGPSPORT) keep the original .fit at rawDetailsPath —
     * it is copied verbatim. For any other device the FIT is synthesized from the
     * summary (and the activity track, if one is available).
     */
    private suspend fun buildFitFile(workout: Workout): File = withContext(Dispatchers.IO) {
        WorkoutUploader.buildFitFile(requireContext(), gbDevice, workout.summary, workout.data)
    }

    private fun exportFit(workout: Workout) {
        lifecycleScope.launch {
            try {
                val targetFile = buildFitFile(workout)
                AndroidUtils.shareFile(requireContext(), targetFile, "application/octet-stream")
            } catch (e: Exception) {
                LOG.error("Failed to export FIT file", e)
                GB.toast(
                    requireContext(),
                    getString(R.string.activity_detail_export_fit_failed),
                    Toast.LENGTH_LONG,
                    GB.ERROR,
                    e
                )
            }
        }
    }

    private fun getGBDevice(device: Device): GBDevice {
        return device.let { findDevice ->
            GBApplication.app().deviceManager.devices
                .first { it.address.equals(findDevice.identifier, ignoreCase = true) }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutDetailsFragment::class.java)

        private const val ARG_WORKOUT_ID = "workout_id"

        fun newInstance(workoutId: Long): WorkoutDetailsFragment {
            return WorkoutDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_WORKOUT_ID, workoutId)
                }
            }
        }
    }
}
