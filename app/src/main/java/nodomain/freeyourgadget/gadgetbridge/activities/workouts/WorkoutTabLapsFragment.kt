package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWorkoutTabLapsBinding
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutViewModel
import nodomain.freeyourgadget.gadgetbridge.util.GridTableBuilder
import org.apache.commons.lang3.tuple.Pair
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class WorkoutTabLapsFragment : Fragment(), WorkoutTabScreenshotProvider {
    private lateinit var viewModel: WorkoutViewModel

    private val workoutValueFormatter = WorkoutValueFormatter()

    private lateinit var binding: FragmentWorkoutTabLapsBinding

    override val screenshotView: View get() = binding.root

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
        binding = FragmentWorkoutTabLapsBinding.inflate(inflater, container, false)

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
        binding.summaryDetails.removeAllViews()
        val groups = ActivitySummaryGroup.buildGroupedList(workout.data)
        for ((groupKey, entries) in groups) {
            if (ActivitySummaryEntries.GROUP_LAPS == groupKey ||
                ActivitySummaryEntries.GROUP_INTERVALS == groupKey
            ) {
                if (!entries.isEmpty()) {
                    addGroupContent(entries)
                }
            }
        }
    }

    private fun addGroupContent(entries: List<Pair<String, ActivitySummaryEntry>>) {
        val gridTableBuilder = GridTableBuilder(requireContext(), workoutValueFormatter)
        for ((key, entry) in entries) {
            gridTableBuilder.addEntry(workoutValueFormatter.getStringResourceByName(key), entry)
        }
        binding.summaryDetails.addView(gridTableBuilder.build())
    }

}