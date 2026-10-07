package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorkoutDetailsBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getSerializableCompat

class WorkoutDetailsActivity : AbstractGBActivity() {
    private val viewModel: WorkoutDetailsViewModel by viewModels()
    private lateinit var binding: ActivityWorkoutDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()

        if (savedInstanceState == null) {
            loadWorkout()
        }
    }

    private fun loadWorkout() {
        val bundle = intent.extras ?: return

        val gbDevice = bundle.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        val singleWorkoutId = bundle.getLong(EXTRA_WORKOUT_ID, -1L)

        if (singleWorkoutId != -1L) {
            // Single workout
            lifecycleScope.launch {
                viewModel.loadSingleWorkout(singleWorkoutId)
            }
        } else {
            // Filtered list
            val position = bundle.getInt("position", 0)
            val activityFilter = bundle.getInt("activityFilter", 0)
            val dateFromFilter = bundle.getLong("dateFromFilter", 0)
            val dateToFilter = bundle.getLong("dateToFilter", 0)
            val deviceFilter = bundle.getLong("deviceFilter", 0)
            val nameContainsFilter = bundle.getString("nameContainsFilter")

            val itemsFilter: List<Long>? = bundle.getSerializableCompat<ArrayList<Long>>("itemsFilter")

            lifecycleScope.launch {
                viewModel.loadFilteredWorkout(
                    gbDevice,
                    activityFilter,
                    dateFromFilter,
                    dateToFilter,
                    nameContainsFilter,
                    deviceFilter,
                    itemsFilter,
                    position
                )
            }
        }
    }

    private fun setupViewModel() {
        viewModel.workoutId.observe(this) { workoutId ->
            if (supportFragmentManager.findFragmentById(binding.fragmentContainer.id) == null) {
                supportFragmentManager.beginTransaction()
                    .replace(binding.fragmentContainer.id, WorkoutDetailsFragment.newInstance(workoutId))
                    .commit()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            showLoading(isLoading)
        }

        viewModel.error.observe(this) { error ->
            error?.let { showError(it) }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.fragmentContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.errorMessage.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingSpinner.visibility = View.GONE
        binding.fragmentContainer.visibility = View.GONE
        binding.errorMessage.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }

    companion object {
        const val EXTRA_WORKOUT_ID = "workout_id"
        const val RESULT_WORKOUT_CHANGED = 2

        fun createSingleWorkoutIntent(
            context: Context,
            workoutId: Long,
            gbDevice: GBDevice
        ): Intent {
            return Intent(context, WorkoutDetailsActivity::class.java).apply {
                putExtra(EXTRA_WORKOUT_ID, workoutId)
                putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
            }
        }

        fun createFilteredListIntent(
            context: Context,
            gbDevice: GBDevice,
            position: Int = 0,
            activityFilter: Int = 0,
            dateFromFilter: Long = 0,
            dateToFilter: Long = 0,
            nameContainsFilter: String? = null,
            deviceFilter: Long = 0,
            itemsFilter: List<Long>? = null
        ): Intent {
            return Intent(context, WorkoutDetailsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
                putExtra("position", position)
                putExtra("activityFilter", activityFilter)
                putExtra("dateFromFilter", dateFromFilter)
                putExtra("dateToFilter", dateToFilter)
                nameContainsFilter?.let { putExtra("nameContainsFilter", it) }
                putExtra("deviceFilter", deviceFilter)
                itemsFilter?.let { putExtra("itemsFilter", ArrayList(it)) }
            }
        }
    }
}
