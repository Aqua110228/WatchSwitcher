package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputEditText
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.ChoiceRowEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.TextValueEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildChoiceEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildTextEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.errorTextView
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatPairValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatScalarValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.hideKeyboard
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.setFieldError
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.showChoicePicker
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.showPairPicker
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.showScalarPicker
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.tapField
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutMeasurementSystem
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimDrill
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimStroke
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.StepFieldsSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.ValueSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateValidator
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat

/**
 * Edits one [WorkoutStepNode], passed in [EXTRA_STEP] and returned in the activity result. The
 * fields are built from the [SportSpec] of the selected step type.
 */
class WorkoutStepEditActivity : AbstractGBActivity() {
    private lateinit var sportSpec: SportSpec
    private lateinit var node: WorkoutStepNode

    private lateinit var device: GBDevice

    private var exerciseCatalog: WorkoutExerciseCatalog? = null

    private lateinit var dynamicFields: LinearLayout
    private lateinit var noteEditor: TextValueEditor
    private lateinit var stepTypeError: TextView
    private var selectedStepType: WorkoutStepType = WorkoutStepType.ACTIVE

    /**
     * Whether [refreshFieldErrors] is a no-op. True for a new step until its first edit, so an
     * incomplete step shows no errors before the user touches it.
     */
    private var suppressErrors = true

    private var durationGroup: TypeGroup<WorkoutDurationType>? = null
    private var targetGroup: TypeGroup<WorkoutTargetType>? = null
    private var secondaryTargetGroup: TypeGroup<WorkoutTargetType>? = null
    private var exerciseGroup: ExerciseGroup? = null
    private var weightGroup: TypeGroup<WorkoutWeightType>? = null
    private var strokeEditor: ChoiceRowEditor<WorkoutSwimStroke>? = null
    private var drillEditor: ChoiceRowEditor<WorkoutSwimDrill>? = null
    private var equipmentEditor: ChoiceRowEditor<WorkoutEquipment>? = null
    private var unitEditor: ChoiceRowEditor<WorkoutMeasurementSystem>? = null

    /**
     * The [ExerciseGroup] that receives the result of [WorkoutExercisePickerActivity], set right
     * before it is launched.
     */
    private var pendingExerciseGroup: ExerciseGroup? = null

    private val exercisePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val group = pendingExerciseGroup
            pendingExerciseGroup = null
            if (result.resultCode != RESULT_OK || group == null) return@registerForActivityResult
            val id = result.data?.getStringExtra(WorkoutExercisePickerActivity.EXTRA_RESULT_ID)
                ?: return@registerForActivityResult
            group.applyPicked(id)
        }

    internal fun launchExercisePicker(group: ExerciseGroup) {
        pendingExerciseGroup = group
        val intent = Intent(this, WorkoutExercisePickerActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        group.list?.let { intent.putExtra(WorkoutExercisePickerActivity.EXTRA_EXERCISE_LIST, it) }
        exercisePickerLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gbDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        val activityKind = ActivityKind.fromCode(intent.getIntExtra(EXTRA_ACTIVITY_KIND, -1))
        val saved = savedInstanceState?.takeIf { it.containsKey(EXTRA_STEP) }
        val step = saved?.getParcelableCompat<WorkoutStepNode>(EXTRA_STEP)
            ?: intent.getParcelableCompat<WorkoutStepNode>(EXTRA_STEP)
        val deviceSpec = gbDevice?.deviceCoordinator?.getWorkoutTemplateSpec(gbDevice)
        val spec = deviceSpec?.sports?.get(activityKind)
        if (gbDevice == null || spec == null || step == null) {
            finish()
            return
        }
        device = gbDevice
        exerciseCatalog = deviceSpec.exerciseCatalog
        sportSpec = spec
        node = step
        selectedStepType = node.stepType ?: sportSpec.stepTypes.firstOrNull() ?: WorkoutStepType.ACTIVE
        suppressErrors = saved?.getBoolean(STATE_SUPPRESS_ERRORS)
            ?: intent.getBooleanExtra(EXTRA_IS_NEW, false)

        setContentView(R.layout.activity_workout_step_edit)
        title = getString(R.string.workout_template_edit_step)

        val stepTypeContainer = findViewById<LinearLayout>(R.id.workout_step_type_container)
        val noteContainer = findViewById<LinearLayout>(R.id.workout_step_note_container)
        dynamicFields = findViewById(R.id.workout_step_dynamic_fields)

        noteEditor = buildTextEditor(
            this,
            getString(R.string.note),
            node.note,
            sportSpec.stepNoteMaxLength ?: 200
        )
        noteContainer.addView(noteEditor.view)

        val stepTypeEditor = buildChoiceEditor(
            this,
            sportSpec.stepTypes,
            getString(R.string.workout_field_step_type),
            selectedStepType,
            onChanged = { picked ->
                selectedStepType = picked
                suppressErrors = false
                rebuildDynamicFields()
            },
            labelFor = { st -> getString(if (st == WorkoutStepType.ACTIVE) sportSpec.activeLabel else st.label) },
        )
        stepTypeContainer.addView(stepTypeEditor.view)
        stepTypeError = errorTextView(this)
        stepTypeContainer.addView(stepTypeError)

        rebuildDynamicFields()

        findViewById<View>(R.id.workout_step_save).setOnClickListener { save() }
        findViewById<View>(R.id.workout_step_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::node.isInitialized) return
        applyUiToNode()
        outState.putParcelable(EXTRA_STEP, node)
        outState.putBoolean(STATE_SUPPRESS_ERRORS, suppressErrors)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Validates the current UI state against [sportSpec] and shows or clears the error of each
     * field. A no-op while [suppressErrors] is set.
     */
    private fun refreshFieldErrors() {
        if (suppressErrors) return
        applyUiToNode()
        val issues = WorkoutTemplateValidator.validateStepFields(node, sportSpec, exerciseCatalog)
        setFieldError(stepTypeError, issues.stepType)
        durationGroup?.setError(issues.duration)
        targetGroup?.setError(issues.target)
        secondaryTargetGroup?.setError(issues.secondaryTarget)
        exerciseGroup?.setError(issues.exercise)
    }

    private fun rebuildDynamicFields() {
        dynamicFields.removeAllViews()
        val stepSpec = sportSpec.stepSpec(selectedStepType)

        durationGroup = if (stepSpec.durations.isEmpty()) {
            null
        } else {
            // A step with no duration defaults to a button press, not to the first declared type
            val initialDurationType = node.duration?.type
                ?: stepSpec.durations.map { it.type }.firstOrNull { it == WorkoutDurationType.BUTTON_PRESS }
            TypeGroup(
                this, dynamicFields, getString(R.string.activity_detail_duration_label),
                stepSpec.durations.map { it.type to it.value },
                initialDurationType, null, node.duration?.value, null,
                onValueChanged = ::onFieldChanged,
            )
        }

        targetGroup = if (stepSpec.primaryTargets.all { it.type == WorkoutTargetType.NONE }) {
            null
        } else {
            TypeGroup(
                this, dynamicFields, getString(R.string.target),
                stepSpec.primaryTargets.map { it.type to it.value },
                node.target?.type, node.target?.zone, node.target?.low, node.target?.high, node.target?.enumValue,
                onValueChanged = ::onPrimaryTargetChanged,
            )
        }

        secondaryTargetGroup = if (stepSpec.secondaryTargets.isEmpty()) {
            null
        } else {
            TypeGroup(
                this,
                dynamicFields,
                getString(R.string.workout_field_secondary_target),
                stepSpec.secondaryTargets.map { it.type to it.value },
                node.secondaryTarget?.type ?: WorkoutTargetType.NONE,
                node.secondaryTarget?.zone,
                node.secondaryTarget?.low,
                node.secondaryTarget?.high,
                node.secondaryTarget?.enumValue,
                isAvailable = { type -> targetGroup?.selectedType()?.let { !type.conflictsWith(it) } ?: true },
                onValueChanged = ::onFieldChanged,
            )
        }

        buildFieldsSection(stepSpec.fields)
        refreshFieldErrors()
    }

    /**
     * Clears [suppressErrors] and refreshes the field errors.
     */
    private fun onFieldChanged() {
        suppressErrors = false
        refreshFieldErrors()
    }

    /**
     * Clears the secondary target when it applies to the same metric as the new primary target.
     */
    private fun onPrimaryTargetChanged() {
        val primary = targetGroup?.selectedType()
        val secondary = secondaryTargetGroup
        if (primary != null && secondary?.selectedType()?.conflictsWith(primary) == true) {
            secondary.resetTo(WorkoutTargetType.NONE)
        }
        onFieldChanged()
    }

    private fun buildFieldsSection(fields: StepFieldsSpec) {
        exerciseGroup = null
        weightGroup = null
        strokeEditor = null
        drillEditor = null
        equipmentEditor = null
        unitEditor = null

        if (fields.exerciseLabel != null) {
            exerciseGroup = ExerciseGroup(
                this,
                dynamicFields,
                getString(fields.exerciseLabel),
                exerciseCatalog,
                fields.exerciseList,
                node.exerciseId,
            )
        }

        if (fields.weightTypes.isNotEmpty()) {
            weightGroup = TypeGroup(
                this,
                dynamicFields,
                getString(R.string.menuitem_weight),
                fields.weightTypes.map { it to fields.weightSpecs[it] },
                node.weightType,
                null,
                node.weightValue?.toLong(),
                null,
                onValueChanged = ::onFieldChanged,
            )
        }

        if (fields.showStroke) {
            val editor = buildChoiceEditor(
                this,
                WorkoutSwimStroke.entries,
                getString(R.string.swimming_stroke),
                node.swimStroke
            )
            strokeEditor = editor
            dynamicFields.addView(editor.view)
        }

        if (fields.showDrill) {
            val editor = buildChoiceEditor(
                this,
                WorkoutSwimDrill.entries,
                getString(R.string.swim_style_drill),
                node.swimDrill ?: WorkoutSwimDrill.NONE,
            )
            drillEditor = editor
            dynamicFields.addView(editor.view)
        }

        if (fields.equipmentOptions.isNotEmpty()) {
            val editor = buildChoiceEditor(
                this,
                fields.equipmentOptions,
                getString(R.string.workout_field_equipment),
                node.swimEquipment ?: WorkoutEquipment.NONE,
            )
            equipmentEditor = editor
            dynamicFields.addView(editor.view)
        }

        if (fields.showUnit) {
            val initialUnit = node.measurementSystem ?: WorkoutMeasurementSystem.globalDefault()
            val editor = buildChoiceEditor(
                this,
                WorkoutMeasurementSystem.entries,
                getString(R.string.workout_field_unit),
                initialUnit
            )
            unitEditor = editor
            dynamicFields.addView(editor.view)
        }
    }

    /**
     * Writes the current UI state into [node].
     */
    private fun applyUiToNode() {
        node.stepType = selectedStepType
        node.note = noteEditor.value.ifBlank { null }

        val durationType = durationGroup?.selectedType()
        node.duration = durationType?.let { WorkoutDuration(it, durationGroup?.currentLow()) }

        val targetType = targetGroup?.selectedType() ?: WorkoutTargetType.NONE
        node.target = WorkoutTarget(
            targetType,
            targetGroup?.currentZone(),
            targetGroup?.currentLow(),
            targetGroup?.currentHigh(),
            targetGroup?.currentEnumValue()
        )

        val secondaryType = secondaryTargetGroup?.selectedType()
        node.secondaryTarget = secondaryType?.let {
            WorkoutTarget(
                it,
                secondaryTargetGroup?.currentZone(),
                secondaryTargetGroup?.currentLow(),
                secondaryTargetGroup?.currentHigh(),
                secondaryTargetGroup?.currentEnumValue()
            )
        }

        node.exerciseId = exerciseGroup?.exerciseId()
        node.weightType = weightGroup?.selectedType()
        node.weightValue = weightGroup?.currentLow()?.toInt()
        node.swimStroke = strokeEditor?.value
        node.swimDrill = drillEditor?.value
        node.swimEquipment = equipmentEditor?.value
        node.measurementSystem = unitEditor?.value
    }

    private fun save() {
        applyUiToNode()
        val result = Intent().putExtra(EXTRA_STEP, node)
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_ACTIVITY_KIND = "workout_step_activity_kind"
        const val EXTRA_STEP = "workout_step_node"

        /**
         * Boolean extra, true for a new step. See [suppressErrors].
         */
        const val EXTRA_IS_NEW = "workout_step_is_new"

        /**
         * Instance state key of [suppressErrors].
         */
        private const val STATE_SUPPRESS_ERRORS = "workout_step_suppress_errors"
    }
}

/**
 * A field that displays the picked exercise and opens [WorkoutExercisePickerActivity] on tap.
 * [activity] launches the picker and passes the result to [applyPicked].
 */
internal class ExerciseGroup(
    private val activity: WorkoutStepEditActivity,
    parent: LinearLayout,
    label: CharSequence,
    private val catalog: WorkoutExerciseCatalog?,

    /**
     * The list of [catalog] the picker shows, see [StepFieldsSpec.exerciseList].
     */
    val list: String?,

    initialExerciseId: String?,
) {
    private var selectedExerciseId: String? = initialExerciseId
    private val errorText = errorTextView(activity)
    private val field: TextInputEditText

    init {
        val (layout, editText) = tapField(activity, label)
        field = editText
        refresh()
        field.setOnClickListener {
            hideKeyboard(field)
            activity.launchExercisePicker(this)
        }
        parent.addView(layout)
        parent.addView(errorText)
    }

    fun setError(@StringRes message: Int?) = setFieldError(errorText, message)

    private fun refresh() {
        field.setText(catalog?.byId(selectedExerciseId)?.name ?: "")
    }

    fun applyPicked(id: String) {
        selectedExerciseId = id
        refresh()
    }

    fun exerciseId(): String? = selectedExerciseId
}

/**
 * A field with a type and a value, for a duration, a target or a weight. A tap opens the type
 * dialog over [options], then the value dialog of the picked type. The field displays the type
 * label followed by the value.
 *
 * [isAvailable] filters [options] when the type dialog opens. [onValueChanged] runs after either
 * dialog is confirmed.
 */
private class TypeGroup<T : LabeledEntry>(
    private val context: Context,
    parent: LinearLayout,
    private val hint: CharSequence,
    private val options: List<Pair<T, ValueSpec?>>,
    initialType: T?,
    initialZone: Int?,
    initialLow: Long?,
    initialHigh: Long?,
    initialEnumValue: String? = null,
    private val isAvailable: (T) -> Boolean = { true },
    private val onValueChanged: () -> Unit = {},
) {
    private var selectedIndex: Int = options.indexOfFirst { it.first == initialType }.let { if (it >= 0) it else 0 }
    private var zone: Int? = initialZone
    private var low: Long? = initialLow
    private var high: Long? = initialHigh
    private var enumValue: String? = initialEnumValue

    private val errorText = errorTextView(context)
    private val field: TextInputEditText

    init {
        val (layout, editText) = tapField(context, hint)
        field = editText
        refresh()
        field.setOnClickListener {
            hideKeyboard(field)
            pickType()
        }
        parent.addView(layout)
        parent.addView(errorText)
    }

    fun setError(@StringRes message: Int?) = setFieldError(errorText, message)

    private fun pickType() {
        val typeOptions = options.map { it.first }.filter(isAvailable)
        if (typeOptions.isEmpty()) return
        showChoicePicker(context, typeOptions, hint, options.getOrNull(selectedIndex)?.first) { picked ->
            val newIndex = options.indexOfFirst { it.first == picked }
            if (newIndex < 0) return@showChoicePicker
            if (newIndex == selectedIndex) {
                pickValue()
                return@showChoicePicker
            }
            // A new type starts with no value. A cancel of the value dialog restores the old type
            // and value.
            val previousIndex = selectedIndex
            val previousZone = zone
            val previousLow = low
            val previousHigh = high
            val previousEnumValue = enumValue

            selectedIndex = newIndex
            zone = null
            low = null
            high = null
            enumValue = null
            pickValue(onCancel = {
                selectedIndex = previousIndex
                zone = previousZone
                low = previousLow
                high = previousHigh
                enumValue = previousEnumValue
            })
        }
    }

    /**
     * Opens the value dialog of the selected type. A type with no value only refreshes the field.
     */
    private fun pickValue(onCancel: () -> Unit = {}) {
        val spec = options[selectedIndex].second
        if (spec == null) {
            refresh()
            onValueChanged()
            return
        }
        val typeLabel = context.getString(options[selectedIndex].first.label)
        when (spec) {
            is ValueSpec.PairRange -> {
                showPairPicker(
                    context,
                    spec,
                    typeLabel,
                    context.getString(R.string.workout_field_minimum),
                    context.getString(R.string.workout_field_maximum),
                    low,
                    high,
                    onCancel = onCancel,
                ) { newLow, newHigh ->
                    low = newLow
                    high = newHigh
                    refresh()
                    onValueChanged()
                }
            }

            is ValueSpec.Zone -> {
                showScalarPicker(context, spec, typeLabel, zone?.toLong(), onCancel = onCancel) { picked ->
                    zone = picked.toInt()
                    refresh()
                    onValueChanged()
                }
            }

            is ValueSpec.EnumValues<*> -> {
                val initial = spec.values.indexOfFirst { (it as Enum<*>).name == enumValue }
                    .let { if (it >= 0) it.toLong() else null }
                showScalarPicker(context, spec, typeLabel, initial, onCancel = onCancel) { picked ->
                    enumValue = (spec.values.getOrNull(picked.toInt()) as? Enum<*>)?.name
                    refresh()
                    onValueChanged()
                }
            }

            else -> {
                showScalarPicker(context, spec, typeLabel, low, onCancel = onCancel) { picked ->
                    low = picked
                    refresh()
                    onValueChanged()
                }
            }
        }
    }

    private fun refresh() {
        val typeLabel = options.getOrNull(selectedIndex)?.first?.let { context.getString(it.label) }
        field.setText(listOfNotNull(typeLabel, currentValueText()).joinToString(" "))
    }

    private fun currentValueText(): String? {
        val spec = options.getOrNull(selectedIndex)?.second ?: return null
        return when (spec) {
            is ValueSpec.PairRange -> {
                val l = low
                val h = high
                if (l != null && h != null) formatPairValue(context, spec, l, h) else null
            }

            is ValueSpec.Zone -> zone?.let { formatScalarValue(context, spec, it.toLong()) }
            is ValueSpec.EnumValues<*> -> enumValue
                ?.let { name -> spec.values.find { (it as Enum<*>).name == name } }
                ?.let { context.getString(it.label) }

            else -> low?.let { formatScalarValue(context, spec, it) }
        }
    }

    /**
     * Selects [type] with no value.
     */
    fun resetTo(type: T) {
        selectedIndex = options.indexOfFirst { it.first == type }.coerceAtLeast(0)
        zone = null
        low = null
        high = null
        enumValue = null
        refresh()
    }

    fun selectedType(): T? = options.getOrNull(selectedIndex)?.first
    fun currentZone(): Int? = zone
    fun currentLow(): Long? = low
    fun currentHigh(): Long? = high

    /**
     * The name of the selected entry of a [ValueSpec.EnumValues].
     */
    fun currentEnumValue(): String? = enumValue
}
