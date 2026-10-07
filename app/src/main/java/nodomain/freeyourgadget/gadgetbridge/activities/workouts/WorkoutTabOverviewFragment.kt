package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWorkoutTabOverviewBinding
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.Device
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutViewModel
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.GridTableBuilder
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectWorkoutSync
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class WorkoutTabOverviewFragment : Fragment(), WorkoutTabScreenshotProvider {
    private lateinit var viewModel: WorkoutViewModel

    private val workoutValueFormatter = WorkoutValueFormatter()

    private lateinit var binding: FragmentWorkoutTabOverviewBinding

    override val screenshotView: View get() = binding.root

    private var gpsFragment: WorkoutGpsFragment? = null

    private var latestWorkout: Workout? = null

    private val workoutId: Long by lazy {
        requireArguments().getLong("workoutId")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())
            .get(WorkoutViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWorkoutTabOverviewBinding.inflate(inflater, container, false)

        // Toggle raw data (shared across all tabs via the view model) on long press of the
        // header photo.
        binding.headerphoto.setOnLongClickListener {
            viewModel.toggleRawData()
            false
        }

        gpsFragment = WorkoutGpsFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.gpsFragmentHolder, gpsFragment!!)
            .commit()

        viewModel.getWorkout(workoutId).distinctUntilChanged().observe(viewLifecycleOwner) { workout ->
            workout ?: return@observe
            latestWorkout = workout
            workoutValueFormatter.setActivityKind(ActivityKind.fromCode(workout.summary.activityKind))
            renderWorkout(workout)
        }
        viewModel.showRawData.observe(viewLifecycleOwner) { showRawData ->
            workoutValueFormatter.setRawData(showRawData)
            latestWorkout?.let { renderWorkout(it) }
        }

        return binding.root
    }

    private fun renderWorkout(workout: Workout) {
        updateHeaderPhoto(workout.summary)
        updateWorkoutHeader(workout.summary)
        binding.summaryDetails.removeAllViews()
        val groups = ActivitySummaryGroup.buildGroupedList(workout.data)
        for ((groupKey, entries) in groups) {
            if (ActivitySummaryEntries.GROUP_ACTIVITY == groupKey) {
                if (!entries.isEmpty()) {
                    addActivityStatTiles(entries)
                }
            }
        }
        // Strength-training workouts: shown inline here rather than as their own tab, since
        // there's usually little else on Overview for these workouts.
        val setsEntries = groups[ActivitySummaryEntries.SETS]
        if (!setsEntries.isNullOrEmpty()) {
            addGroupHeader(ActivitySummaryEntries.SETS)
            addGroupContent(setsEntries)
        }
        updateGpsMap(workout)
    }

    private fun updateGpsMap(workout: Workout) {
        if (!workoutHasGps(workout)) {
            binding.gpsFragmentHolder.visibility = View.GONE
            return
        }

        binding.gpsFragmentHolder.visibility = View.VISIBLE
        gpsFragment?.setTrackData(workout.summary, getGBDevice(workout.summary.device))
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

    private fun getGBDevice(device: Device): GBDevice {
        return GBApplication.app().deviceManager.devices
            .first { it.address.equals(device.identifier, ignoreCase = true) }
    }

    private fun addGroupContent(entries: List<Pair<String, ActivitySummaryEntry>>) {
        val gridTableBuilder = GridTableBuilder(requireContext(), workoutValueFormatter)
        for ((key, entry) in entries) {
            gridTableBuilder.addEntry(workoutValueFormatter.getStringResourceByName(key), entry)
        }
        binding.summaryDetails.addView(gridTableBuilder.build())
    }

    private fun addActivityStatTiles(entries: List<Pair<String, ActivitySummaryEntry>>) {
        val stats = entries.mapNotNull { (key, entry) ->
            (entry as? ActivitySummarySimpleEntry)?.takeIf { it.value != null }?.let {
                StatTileData(workoutValueFormatter.formatTileValue(it.value, it.unit), workoutValueFormatter.getStringResourceByName(key))
            }
        }
        if (stats.isNotEmpty()) {
            addStatTileGrid(binding.summaryDetails, requireContext(), stats, horizontalMarginDp = 16)
        }
    }

    private fun addGroupHeader(groupKey: String) {
        val labelField = TextView(context).apply {
            id = View.generateViewId()
            textSize = 18f
            gravity = Gravity.START
            setPaddingRelative(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(GBApplication.getTextColor(context))
            text = workoutValueFormatter.getStringResourceByName(groupKey)
        }
        binding.summaryDetails.addView(labelField)
    }

    @Suppress("SameParameterValue")
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun updateHeaderPhoto(summary: BaseActivitySummary) {
        val headerPhoto = summary.headerPhoto
        if (headerPhoto == null) {
            binding.headerphoto.visibility = View.GONE
            binding.headerphoto.setImageDrawable(null)
        } else {
            binding.headerphoto.visibility = View.VISIBLE
            binding.headerphoto.setImageURI(Uri.fromFile(File(headerPhoto)))
        }
    }

    private fun String.keepTogether(): String = replace(' ', '\u00A0')

    private fun updateWorkoutHeader(summary: BaseActivitySummary) {
        val activityName = summary.name
        val startTime = summary.startTime
        val endTime = summary.endTime
        val durationHms = DateTimeUtils.formatDurationHoursMinutes(
            endTime.time - startTime.time, TimeUnit.MILLISECONDS
        )

        view?.let {
            binding.itemImage.setImageResource(
                ActivityKind.fromCode(summary.activityKind).icon
            )

            // Activity name
            binding.activityname.apply {
                text = activityName
                setTextColor(GBApplication.getTextColor(context))
                visibility = if (StringUtils.isBlank(activityName)) View.GONE else View.VISIBLE
            }

            // Date range and duration share one line. Non-breaking spaces keep the time range and
            // the duration in one piece, so at large font scales the line only wraps at the
            // separator and never inside a time.
            val dateString = if (DateTimeUtils.isSameDay(startTime, endTime)) {
                val endTimeCal = Calendar.getInstance().apply { time = endTime }
                requireContext().getString(
                    R.string.date_placeholders__start_time__end_time,
                    DateTimeUtils.formatDateTimeRelative(requireContext(), startTime),
                    DateTimeUtils.formatTime(
                        endTimeCal.get(Calendar.HOUR_OF_DAY),
                        endTimeCal.get(Calendar.MINUTE)
                    )
                ).keepTogether()
            } else {
                requireContext().getString(
                    R.string.date_placeholders__start_time__end_time,
                    DateTimeUtils.formatDateTimeRelative(requireContext(), startTime),
                    DateTimeUtils.formatDateTimeRelative(requireContext(), endTime)
                )
            }
            binding.activitymeta.text = "$dateString · ${durationHms.keepTogether()}"

            updateUploadStatusIcon(summary)
            updateHealthConnectStatusIcon(summary)
        }
    }

    /**
     * Fills in the header's upload indicator, the same state the workout list shows on each row,
     * and makes it open the per-service breakdown. Stays hidden while it resolves, and when no
     * service is set up and none ever took this workout.
     */
    private fun updateUploadStatusIcon(summary: BaseActivitySummary) {
        binding.uploadStatusIcon.visibility = View.GONE
        val summaryId = summary.id ?: return
        val context = requireContext()
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                val targets = WorkoutUploadTargets.active(context, GBApplication.getPrefs())
                val rows = WorkoutUploadStore.rowsForSummaries(listOf(summaryId))[summaryId].orEmpty()
                workoutUploadStatus(summary, rows, targets)
            } ?: return@launch
            if (!isAdded) return@launch
            binding.uploadStatusIcon.apply {
                setImageResource(status.iconRes)
                contentDescription = getString(status.labelRes)
                setOnClickListener { (parentFragment as? WorkoutDetailsFragment)?.showUploadStatus(summary) }
                visibility = View.VISIBLE
            }
        }
    }

    /** Fills in the header's Health Connect indicator, hidden unless the workout failed to sync. */
    private fun updateHealthConnectStatusIcon(summary: BaseActivitySummary) {
        binding.healthConnectStatusIcon.visibility = View.GONE
        val summaryId = summary.id ?: return
        val context = requireContext()
        lifecycleScope.launch {
            val failure = withContext(Dispatchers.IO) { HealthConnectWorkoutSync.failureOf(summaryId) }
            if (failure == null || !isAdded) return@launch
            val message = getString(
                R.string.health_connect_workout_sync_failed_reason,
                DateTimeUtils.formatDateTimeRelative(context, Date(failure.failedAt)),
                failure.error.orEmpty()
            )
            binding.healthConnectStatusIcon.apply {
                setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.healthconnect_settings)
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
                visibility = View.VISIBLE
            }
        }
    }
}
