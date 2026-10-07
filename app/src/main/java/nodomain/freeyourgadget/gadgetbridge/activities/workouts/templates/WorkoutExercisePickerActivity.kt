package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorkoutExercisePickerBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExercise
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat

/**
 * Picks an exercise from the list [EXTRA_EXERCISE_LIST] of the device's catalog, with a text
 * search and the filters of the catalog. Returns the [WorkoutExercise.id] in [EXTRA_RESULT_ID].
 */
class WorkoutExercisePickerActivity : AbstractGBActivity() {
    private val viewModel: WorkoutExercisePickerViewModel by viewModels()
    private lateinit var adapter: ExerciseAdapter
    private lateinit var binding: ActivityWorkoutExercisePickerBinding
    private var filterItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWorkoutExercisePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.workout_exercise_picker_title)

        val device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        val catalog = device?.deviceCoordinator?.getWorkoutTemplateSpec(device)?.exerciseCatalog
        if (catalog == null) {
            finish()
            return
        }

        adapter = ExerciseAdapter()
        binding.workoutExercisePickerList.apply {
            layoutManager = LinearLayoutManager(this@WorkoutExercisePickerActivity)
            adapter = this@WorkoutExercisePickerActivity.adapter
        }

        lifecycleScope.launch {
            viewModel.filters.collect { filters ->
                filterItem?.isVisible = filters.isNotEmpty()
                showActiveFilters()
            }
        }
        lifecycleScope.launch {
            viewModel.exercises.collect { showExercises(it) }
        }

        viewModel.load(catalog, intent.getStringExtra(EXTRA_EXERCISE_LIST))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.workout_exercise_picker_menu, menu)
        val searchView = menu.findItem(R.id.workout_exercise_picker_action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(newText: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        filterItem = menu.findItem(R.id.workout_exercise_picker_action_filter)
        filterItem?.isVisible = viewModel.filters.value.isNotEmpty()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.workout_exercise_picker_action_filter) {
            showFilterDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows a dialog with a collapsible section per filter and a checkbox per value.
     */
    private fun showFilterDialog() {
        val filters = viewModel.filters.value
        val content = layoutInflater.inflate(R.layout.dialog_workout_exercise_filters, null)
        val container = content.findViewById<LinearLayout>(R.id.workout_exercise_filters)
        val total = content.findViewById<TextView>(R.id.workout_exercise_filters_count)
        val boxes = mutableMapOf<String, MutableMap<String, CheckBox>>()
        val counts = mutableMapOf<String, MutableMap<String, TextView>>()
        val summaries = mutableMapOf<String, TextView>()
        val sections = mutableMapOf<String, Pair<View, View>>() // filter id -> (values, caret)

        fun picked(): Map<String, Set<String>> =
            boxes.mapValues { (_, values) -> values.filterValues { it.isChecked }.keys }

        var clearing = false

        fun showCounts() {
            if (clearing) return
            val current = picked()
            val matching = viewModel.count(current)
            total.text = resources.getQuantityString(
                R.plurals.workout_exercise_picker_filter_count,
                matching,
                matching
            )
            for ((filterId, values) in counts) {
                for ((valueId, view) in values) {
                    val candidate = current.toMutableMap()
                    candidate[filterId] = current[filterId].orEmpty() + valueId
                    view.text = viewModel.count(candidate).toString()
                }
            }
            for (filter in filters) {
                val checked = current[filter.id].orEmpty()
                val summary = summaries.getValue(filter.id)
                summary.text = filter.values.filter { it.id in checked }.joinToString(", ") { it.name }
                summary.visibility = if (checked.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        fun expand(filterId: String?) {
            for ((id, section) in sections) {
                val (values, caret) = section
                val open = id == filterId
                values.visibility = if (open) View.VISIBLE else View.GONE
                caret.rotation = if (open) 180f else 0f
            }
        }

        for (filter in filters) {
            val row = layoutInflater.inflate(R.layout.item_workout_exercise_filter, container, false)
            val header = row.findViewById<View>(R.id.workout_exercise_filter_header)
            row.findViewById<TextView>(R.id.workout_exercise_filter_name).text = filter.name
            summaries[filter.id] = row.findViewById(R.id.workout_exercise_filter_summary)

            val values = row.findViewById<LinearLayout>(R.id.workout_exercise_filter_values)
            if (filters.size > 1) {
                sections[filter.id] = values to row.findViewById(R.id.workout_exercise_filter_caret)
                header.setOnClickListener {
                    expand(if (values.visibility == View.VISIBLE) null else filter.id)
                }
            } else {
                header.visibility = View.GONE
                values.visibility = View.VISIBLE
            }
            for (value in filter.values) {
                val valueRow = layoutInflater.inflate(
                    R.layout.item_workout_exercise_filter_value,
                    values,
                    false
                )
                valueRow.findViewById<TextView>(R.id.workout_exercise_filter_value_name).text = value.name
                val box = valueRow.findViewById<CheckBox>(R.id.workout_exercise_filter_value_checkbox)
                box.isChecked = value.id in viewModel.checked[filter.id].orEmpty()
                box.setOnCheckedChangeListener { _, _ -> showCounts() }
                valueRow.setOnClickListener { box.isChecked = !box.isChecked }
                values.addView(valueRow)

                boxes.getOrPut(filter.id) { mutableMapOf() }[value.id] = box
                counts.getOrPut(filter.id) { mutableMapOf() }[value.id] =
                    valueRow.findViewById(R.id.workout_exercise_filter_value_count)
            }
            container.addView(row)
        }
        showCounts()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_exercise_picker_filter_title)
            .setView(content)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.setChecked(picked())
                showActiveFilters()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.clear_current_selection, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            clearing = true
            boxes.values.forEach { values -> values.values.forEach { it.isChecked = false } }
            clearing = false
            showCounts()
        }
    }

    /**
     * Displays the names of the checked filter values as the action bar subtitle.
     */
    private fun showActiveFilters() {
        val labels = viewModel.filters.value.flatMap { filter ->
            val values = viewModel.checked[filter.id].orEmpty()
            filter.values.filter { it.id in values }.map { it.name }
        }
        supportActionBar?.subtitle = if (labels.isEmpty()) null else labels.joinToString(", ")
    }

    /**
     * @param exercises null while the catalog is being read
     */
    private fun showExercises(exercises: List<WorkoutExercise>?) {
        binding.workoutExercisePickerProgress.visibility = if (exercises == null) View.VISIBLE else View.GONE
        binding.workoutExercisePickerEmpty.visibility =
            if (exercises != null && exercises.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(exercises.orEmpty())
    }

    private fun pick(exercise: WorkoutExercise) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_ID, exercise.id))
        finish()
    }

    private inner class ExerciseAdapter : RecyclerView.Adapter<ExerciseAdapter.ViewHolder>() {
        private var items: List<WorkoutExercise> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<WorkoutExercise>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.workout_exercise_picker_item_name)
            val filters: TextView = view.findViewById(R.id.workout_exercise_picker_item_groups)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workout_exercise_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exercise = items[position]
            holder.name.text = exercise.name
            val labels = filterLabels(exercise)
            holder.filters.text = labels
            holder.filters.visibility = if (labels.isEmpty()) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener { pick(exercise) }
        }

        /**
         * The names of the filter values [exercise] matches.
         */
        private fun filterLabels(exercise: WorkoutExercise): String =
            viewModel.filters.value.flatMap { filter ->
                val values = exercise.filters[filter.id].orEmpty()
                filter.values.filter { it.id in values }.map { it.name }
            }.joinToString(", ")
    }

    companion object {
        /**
         * String extra, the list of the catalog to display. Absent for the whole catalog, see
         * [nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.StepFieldsSpec.exerciseList].
         */
        const val EXTRA_EXERCISE_LIST = "workout_exercise_picker_exercise_list"

        /**
         * String extra of the result, the picked [WorkoutExercise.id].
         */
        const val EXTRA_RESULT_ID = "workout_exercise_picker_result_id"
    }
}
