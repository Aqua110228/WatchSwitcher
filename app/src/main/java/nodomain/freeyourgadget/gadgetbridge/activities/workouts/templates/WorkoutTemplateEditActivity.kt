package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.ScalarValueEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.TextValueEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildChoiceEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildScalarEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildTextEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.dp
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatPairValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatScalarValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.hideKeyboard
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorkoutTemplateEditBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.ValueSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutValidationIssue
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat

/**
 * Creates or edits one [nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate]. The
 * step tree is an indented list. A STEP row opens [WorkoutStepEditActivity]. Every row can be
 * dragged in or out of a repeat group, see [StepDragCallback].
 *
 * A multisport template lists its legs, one row each. A leg opens this same screen with
 * [EXTRA_LEG], and the edited leg is returned in the result.
 *
 * A row with a validation error shows a warning icon and its first issue.
 */
class WorkoutTemplateEditActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var spec: WorkoutTemplateSpec
    private lateinit var adapter: StepAdapter
    private val viewModel: WorkoutTemplateEditViewModel by viewModels()

    private lateinit var binding: ActivityWorkoutTemplateEditBinding
    private var nameEditor: TextValueEditor? = null
    private var noteEditor: TextValueEditor? = null
    private var templateReady = false

    /**
     * The exercise catalog, set once it is read. Reading it is slow, so a row shows
     * no exercise name until then.
     */
    private var exerciseCatalog: WorkoutExerciseCatalog? = null

    /**
     * Whether this screen is editting the steps of one multisport leg.
     */
    private val legMode: Boolean
        get() = viewModel.leg != null

    /**
     * The custom pool length field, while [WorkoutPoolLengthUnit.CUSTOM] is selected.
     */
    private var poolLengthCustomEditor: ScalarValueEditor? = null

    /**
     * What to do with the result of [WorkoutStepEditActivity], set right before it is launched.
     */
    private sealed class PendingStep {
        data object AddTopLevel : PendingStep()
        data class AddChild(val parent: WorkoutStepNode) : PendingStep()
        data object EditExisting : PendingStep()
    }

    private var pendingStep: PendingStep? = null

    private val legEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val edited = result.data?.getParcelableCompat<WorkoutStepNode>(EXTRA_LEG)
                ?: return@registerForActivityResult
            viewModel.replaceNode(edited)
        }

    private val stepEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = pendingStep
            pendingStep = null
            if (result.resultCode != RESULT_OK || target == null) return@registerForActivityResult
            val edited = result.data?.getParcelableCompat<WorkoutStepNode>(WorkoutStepEditActivity.EXTRA_STEP)
                ?: return@registerForActivityResult
            when (target) {
                is PendingStep.AddTopLevel -> viewModel.addTopLevel(edited)
                is PendingStep.AddChild -> viewModel.addChild(target.parent, edited)
                is PendingStep.EditExisting -> viewModel.replaceNode(edited)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dev = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (dev == null) {
            finish()
            return
        }
        device = dev
        val deviceSpec = dev.deviceCoordinator.getWorkoutTemplateSpec(dev)
        if (deviceSpec == null) {
            finish()
            return
        }
        spec = deviceSpec

        binding = ActivityWorkoutTemplateEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = StepAdapter()
        binding.workoutTemplateStepList.apply {
            layoutManager = LinearLayoutManager(this@WorkoutTemplateEditActivity)
            adapter = this@WorkoutTemplateEditActivity.adapter
        }
        val touchHelper = ItemTouchHelper(StepDragCallback())
        touchHelper.attachToRecyclerView(binding.workoutTemplateStepList)
        adapter.touchHelper = touchHelper

        binding.workoutTemplateAddStep.setOnClickListener { addStep(parent = autoParentForNewStep()) }
        binding.workoutTemplateAddRepeat.setOnClickListener { editRepeatCount(node = null) }
        binding.workoutTemplateAddLeg.setOnClickListener { addLeg() }
        binding.workoutTemplateSave.setOnClickListener { trySave() }

        onBackPressedDispatcher.addCallback(this) { confirmAndFinish() }

        lifecycleScope.launch {
            viewModel.rows.collect { rows -> adapter.submit(rows, viewModel.validate(spec)) }
        }
        lifecycleScope.launch {
            viewModel.loaded.collect { onTemplateReady() }
        }
        lifecycleScope.launch {
            viewModel.saved.collect {
                setResult(RESULT_TEMPLATE_SAVED)
                finish()
            }
        }

        val catalog = spec.exerciseCatalog
        if (catalog != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { catalog.preload() }
                exerciseCatalog = catalog
                adapter.submit(viewModel.rows.value, viewModel.validate(spec))
            }
        }

        lifecycleScope.launch {
            viewModel.saveFailed.collect {
                Snackbar.make(
                    binding.workoutTemplateStepList,
                    R.string.workout_template_save_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        if (viewModel.started) {
            // The activity was re-created and the view model kept the template. A load that is
            // still running emits to WorkoutTemplateEditViewModel.loaded.
            if (viewModel.hasTemplate) onTemplateReady(restored = true)
            return
        }

        val leg = intent.getParcelableCompat<WorkoutStepNode>(EXTRA_LEG)
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
        if (leg != null) {
            viewModel.startLeg(spec.vendorId, leg)
            onTemplateReady()
        } else if (templateId >= 0) {
            viewModel.load(templateId)
        } else {
            val activityKindCode = intent.getIntExtra(EXTRA_ACTIVITY_KIND, -1)
            viewModel.startNew(spec.vendorId, ActivityKind.fromCode(activityKindCode))
            onTemplateReady()
        }
    }

    /**
     * Builds the template-level fields once [WorkoutTemplateEditViewModel.template] is set. A leg
     * has no template-level fields. [restored] keeps the unsaved changes of the view model after
     * the activity is re-created.
     */
    private fun onTemplateReady(restored: Boolean = false) {
        title = viewModel.template.activityKind.getLabel(this)
        val sportSpec = spec.sports[viewModel.template.activityKind]
        val multisport = !legMode && sportSpec?.legs != null
        binding.workoutTemplateAddStep.visibility = if (multisport) View.GONE else View.VISIBLE
        binding.workoutTemplateAddRepeat.visibility = if (multisport) View.GONE else View.VISIBLE
        binding.workoutTemplateAddLeg.visibility = if (multisport) View.VISIBLE else View.GONE

        if (!legMode) {
            val name = buildTextEditor(
                this,
                getString(R.string.workout_template_name_hint),
                viewModel.template.name,
                spec.nameMaxLength
            ) {
                viewModel.template.name = nameEditor?.value.orEmpty()
                refreshNameError()
            }
            nameEditor = name
            binding.workoutTemplateHeaderContainer.addView(name.view)
            val note = buildTextEditor(
                this,
                getString(R.string.note),
                viewModel.template.note,
                spec.noteMaxLength
            ) {
                viewModel.template.note = noteEditor?.value?.ifBlank { null }
            }
            noteEditor = note
            binding.workoutTemplateHeaderContainer.addView(note.view)
            bindOptionalFields()
        }

        if (!restored) viewModel.markBaseline()
        templateReady = true
    }

    /**
     * Asks for confirmation before leaving with unsaved changes.
     */
    private fun confirmAndFinish() {
        if (!templateReady || !viewModel.hasUnsavedChanges()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_discard_title)
            .setMessage(R.string.workout_template_discard_message)
            .setPositiveButton(R.string.workout_template_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.workout_template_keep_editing, null)
            .show()
    }

    private fun bindOptionalFields() {
        val sportSpec = spec.sports[viewModel.template.activityKind]

        val poolLengthCustomSpec = sportSpec?.poolLengthCustom
        if (poolLengthCustomSpec != null) {
            val poolLengthContainer = binding.workoutTemplatePoolLengthContainer
            val poolLengthCustomContainer = binding.workoutTemplatePoolLengthCustomContainer

            if (viewModel.template.poolLengthUnit == null) {
                viewModel.template.poolLengthUnit = WorkoutPoolLengthUnit.UNSPECIFIED
            }

            poolLengthContainer.visibility = View.VISIBLE
            poolLengthContainer.removeAllViews()
            val editor = buildChoiceEditor(
                this,
                WorkoutPoolLengthUnit.entries,
                getString(R.string.poolLength),
                viewModel.template.poolLengthUnit,
                onChanged = { picked ->
                    viewModel.template.poolLengthUnit = picked
                    if (picked != WorkoutPoolLengthUnit.CUSTOM) viewModel.template.poolLength = null
                    bindPoolLengthCustomField(poolLengthCustomContainer, poolLengthCustomSpec)
                },
            )
            poolLengthContainer.addView(editor.view)
            bindPoolLengthCustomField(poolLengthCustomContainer, poolLengthCustomSpec)
        }

        val transitionsBox = binding.workoutTemplateTransitionsCheckbox
        if (sportSpec?.transitions == true) {
            transitionsBox.visibility = View.VISIBLE
            transitionsBox.isChecked = viewModel.template.transitions == true
            transitionsBox.setOnCheckedChangeListener { _, checked ->
                hideKeyboard(transitionsBox)
                viewModel.template.transitions = checked
            }
        }
    }

    /**
     * Shows the custom pool length field while [WorkoutPoolLengthUnit.CUSTOM] is selected.
     */
    private fun bindPoolLengthCustomField(container: LinearLayout, spec: ValueSpec) {
        container.removeAllViews()
        poolLengthCustomEditor = null
        if (viewModel.template.poolLengthUnit != WorkoutPoolLengthUnit.CUSTOM) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val editor = buildScalarEditor(
            this,
            spec,
            getString(R.string.workout_template_pool_length_custom_hint),
            viewModel.template.poolLength?.toLong()
        )
        poolLengthCustomEditor = editor
        container.addView(editor.view)
    }

    /**
     * The last top-level node, when it is an empty repeat group. A new step goes inside it.
     */
    private fun autoParentForNewStep(): WorkoutStepNode? =
        viewModel.template.steps.lastOrNull()
            ?.takeIf { it.type == WorkoutNodeType.REPEAT && it.children.isEmpty() }

    /**
     * Opens a new step in [WorkoutStepEditActivity]. It is added inside [parent], or at the top
     * level when [parent] is null.
     */
    private fun addStep(parent: WorkoutStepNode?) {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val stepType = sportSpec.stepTypes.firstOrNull() ?: WorkoutStepType.ACTIVE
        val node = WorkoutStepNode.newStep(stepType)
        pendingStep = if (parent != null) PendingStep.AddChild(parent) else PendingStep.AddTopLevel
        launchStepEditor(node, isNew = true)
    }

    /**
     * Opens an existing STEP node in [WorkoutStepEditActivity]. The returned copy replaces it.
     */
    private fun editStep(node: WorkoutStepNode) {
        pendingStep = PendingStep.EditExisting
        launchStepEditor(node, isNew = false)
    }

    private fun launchStepEditor(node: WorkoutStepNode, isNew: Boolean) {
        val intent = Intent(this, WorkoutStepEditActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_ACTIVITY_KIND, viewModel.template.activityKind.code)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_STEP, node)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_IS_NEW, isNew)
        stepEditLauncher.launch(intent)
    }

    /**
     * Shows the repeat count dialog. Adds a new top-level repeat group when [node] is null, or
     * updates the count of [node].
     */
    private fun editRepeatCount(node: WorkoutStepNode?) {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val repeatSpec = sportSpec.repeat ?: return
        hideKeyboard(binding.workoutTemplateStepList)
        val picker = NumberPicker(this).apply {
            minValue = repeatSpec.min
            maxValue = repeatSpec.max
            value = (node?.repeatCount ?: repeatSpec.min).coerceIn(repeatSpec.min, repeatSpec.max)
            wrapSelectorWheel = false
        }
        val container = FrameLayout(this)
        container.setPadding(dp(this, 24), dp(this, 16), dp(this, 24), dp(this, 0))
        container.addView(
            picker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_add_repeat)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val count = picker.value
                if (node != null) {
                    node.repeatCount = count
                    viewModel.refreshRows()
                } else {
                    viewModel.addTopLevel(WorkoutStepNode.newRepeat(count))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addLeg() {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val kinds = sportSpec.legs?.sports ?: return
        hideKeyboard(binding.workoutTemplateStepList)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_multisport_leg_add)
            .setAdapter(SportPickerAdapter(this, kinds)) { _, which ->
                viewModel.addTopLevel(WorkoutStepNode.newLeg(kinds[which]))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Opens the steps of [leg] in a new instance of this screen.
     */
    private fun editLeg(leg: WorkoutStepNode) {
        val intent = Intent(this, WorkoutTemplateEditActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        intent.putExtra(EXTRA_LEG, leg)
        legEditLauncher.launch(intent)
    }

    private fun trySave() {
        nameEditor?.let { viewModel.template.name = it.value }
        noteEditor?.let { viewModel.template.note = it.value.ifBlank { null } }
        if (viewModel.template.poolLengthUnit == WorkoutPoolLengthUnit.CUSTOM) {
            viewModel.template.poolLength = poolLengthCustomEditor?.value?.toInt()
        }

        val issues = viewModel.validate(spec)
        val nameIssue = refreshNameError(issues)
        if (issues.isNotEmpty()) {
            val firstStepIssue = issues.firstOrNull { it.nodeLocalId != null }
            if (firstStepIssue != null) {
                val index = adapter.indexOf(firstStepIssue.nodeLocalId)
                if (index >= 0) binding.workoutTemplateStepList.smoothScrollToPosition(index)
                showValidationSnackbar()
            } else if (nameIssue != null) {
                showValidationSnackbar()
            } else {
                // A template-level issue with no field of its own, such as too many steps
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.workout_template_validation_title)
                    .setMessage(getString(issues.first { it.nodeLocalId == null }.message))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }
        if (legMode) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_LEG, viewModel.legResult()))
            finish()
            return
        }
        viewModel.save(device)
    }

    /**
     * Shows or clears the error of the name field. Returns the name issue, if any.
     */
    private fun refreshNameError(issues: List<WorkoutValidationIssue> = viewModel.validate(spec)): WorkoutValidationIssue? {
        val nameIssue = issues.firstOrNull { it.nodeLocalId == null && isNameIssue(it) }
        nameEditor?.setError(nameIssue?.let { getString(it.message) })
        return nameIssue
    }

    private fun showValidationSnackbar() {
        Snackbar.make(
            binding.workoutTemplateStepList,
            R.string.workout_template_validation_snackbar,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun isNameIssue(issue: WorkoutValidationIssue) =
        issue.message == R.string.workout_error_name_required || issue.message == R.string.workout_error_name_too_long

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            confirmAndFinish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showRowMenu(anchor: View, row: WorkoutStepRow) {
        hideKeyboard(anchor)
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_EDIT, 0, R.string.appmanager_app_edit)
        if (row.node.type == WorkoutNodeType.REPEAT) {
            popup.menu.add(0, MENU_ADD_STEP_INSIDE, 1, R.string.workout_template_add_step_inside)
        }
        val deleteLabel =
            if (row.node.type == WorkoutNodeType.LEG) R.string.workout_template_multisport_leg_delete
            else R.string.workout_template_delete_step
        popup.menu.add(0, MENU_DELETE, 2, deleteLabel)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> {
                    when (row.node.type) {
                        WorkoutNodeType.STEP -> editStep(row.node)
                        WorkoutNodeType.REPEAT -> editRepeatCount(row.node)
                        WorkoutNodeType.LEG -> editLeg(row.node)
                    }
                    true
                }

                MENU_ADD_STEP_INSIDE -> {
                    addStep(parent = row.node)
                    true
                }

                MENU_DELETE -> {
                    viewModel.remove(row.node)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /**
     * The default background of a step row, `?attr/selectableItemBackground`.
     */
    private val defaultRowBackgroundResId: Int by lazy {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        typedValue.resourceId
    }

    /**
     * The background of the rows of a group while it is the drop target of a drag.
     */
    private fun nestedDragBackground(context: Context): Drawable =
        ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.workout_step_color_group), 70)
            .toDrawable()

    /**
     * Tints the row at [index], if it is bound.
     */
    private fun tintRowBackground(recyclerView: RecyclerView, index: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(index) ?: return
        holder.itemView.background = nestedDragBackground(holder.itemView.context)
    }

    private fun resetRowBackground(recyclerView: RecyclerView, index: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(index) ?: return
        holder.itemView.setBackgroundResource(defaultRowBackgroundResId)
    }

    /**
     * Drag-and-drop reordering:
     * - a STEP can be dragged to any position, in or out of any repeat group
     * - a REPEAT group drags as a single block and may only ever be at the top level
     * - a repeat group can never contain another one
     *
     * The adapter reorders its own list during the drag. The tree is rebuilt once, in [clearView],
     * from the final order.
     */
    private inner class StepDragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
    ) {
        /**
         * The id of the dragged node.
         */
        private var draggedLocalId: Long? = null

        /**
         * The depth of the dragged row as last previewed by [onChildDraw]. Null for a REPEAT or LEG.
         */
        private var draggedDepth: Int? = null

        /**
         * The adapter positions currently tinted as the drop target.
         */
        private var highlightedIndices: Set<Int> = emptySet()

        override fun isLongPressDragEnabled(): Boolean = true

        override fun onMove(
            rv: RecyclerView,
            holder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = holder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            adapter.moveItem(from, to)
            return true
        }

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean =
            adapter.canDropOnto(current.bindingAdapterPosition, target.bindingAdapterPosition)

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                val position = viewHolder.bindingAdapterPosition
                val row = adapter.itemAt(position)
                draggedLocalId = row?.node?.localId
                draggedDepth = row?.depth
                adapter.collapseChildrenAt(position)
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive || viewHolder !is StepAdapter.ViewHolder) return
            val position = viewHolder.bindingAdapterPosition
            val row = adapter.itemAt(position) ?: return
            if (row.node.type != WorkoutNodeType.STEP) return
            val depth = adapter.previewDepthAt(position, dY, viewHolder.itemView.height)
            draggedDepth = depth
            val indentPx = dp(recyclerView.context, INDENT_DP) * depth
            if (viewHolder.indent.layoutParams.width != indentPx) {
                viewHolder.indent.layoutParams = viewHolder.indent.layoutParams.apply { width = indentPx }
            }
            // Only the rows whose tint changes are touched
            val newIndices = adapter.groupRangeAt(position, depth)?.toSet() ?: emptySet()
            if (newIndices != highlightedIndices) {
                (highlightedIndices - newIndices).forEach { resetRowBackground(recyclerView, it) }
                (newIndices - highlightedIndices).forEach { tintRowBackground(recyclerView, it) }
                highlightedIndices = newIndices
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            highlightedIndices.forEach { resetRowBackground(recyclerView, it) }
            highlightedIndices = emptySet()
            val order = adapter.finishDrag(draggedLocalId, draggedDepth)
            draggedLocalId = null
            draggedDepth = null
            if (order.isNotEmpty()) viewModel.applyReorder(order)
        }
    }

    private inner class StepAdapter : RecyclerView.Adapter<StepAdapter.ViewHolder>() {
        var touchHelper: ItemTouchHelper? = null

        private var items: MutableList<WorkoutStepRow> = mutableListOf()

        /**
         * The first validation issue of each row, by [WorkoutStepNode.localId].
         */
        private var issuesByNode: Map<Long, WorkoutValidationIssue> = emptyMap()

        /**
         * The children of the dragged REPEAT or LEG, removed from [items] for the duration of the
         * drag.
         */
        private var collapsedChildren: List<WorkoutStepRow>? = null

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<WorkoutStepRow>, issues: List<WorkoutValidationIssue>) {
            items = newItems.toMutableList()
            issuesByNode = issues.mapNotNull { issue -> issue.nodeLocalId?.let { it to issue } }.toMap()
            notifyDataSetChanged()
        }

        /**
         * The position of the row of [nodeLocalId], or -1.
         */
        fun indexOf(nodeLocalId: Long?): Int = items.indexOfFirst { it.node.localId == nodeLocalId }

        fun itemAt(position: Int): WorkoutStepRow? = items.getOrNull(position)

        /**
         * Moves the row at [from] to [to].
         */
        fun moveItem(from: Int, to: Int) {
            val row = items.removeAt(from)
            items.add(to, row)
            notifyItemMoved(from, to)
        }

        /**
         * A REPEAT or LEG row can only be dropped at a top-level position. A STEP can be dropped
         * anywhere.
         */
        fun canDropOnto(currentPosition: Int, targetPosition: Int): Boolean {
            val current = itemAt(currentPosition) ?: return true
            val target = itemAt(targetPosition) ?: return true
            return current.node.type == WorkoutNodeType.STEP || target.depth == 0
        }

        /**
         * Removes the children of the REPEAT or LEG at [position] from the list, into
         * [collapsedChildren].
         */
        fun collapseChildrenAt(position: Int) {
            val row = itemAt(position) ?: return
            if (row.node.type == WorkoutNodeType.STEP) return
            val start = position + 1
            var end = start
            while (end < items.size && items[end].depth > row.depth) end++
            if (end == start) return
            collapsedChildren = items.subList(start, end).toList()
            repeat(end - start) { items.removeAt(start) }
            notifyItemRangeRemoved(start, end - start)
        }

        /**
         * The depths the STEP at [position] can have, given the rows above and below it. The range
         * has two values right after the last child of a group, where the step can stay in the
         * group or leave it.
         */
        fun depthRangeAt(position: Int): IntRange {
            val prev = items.getOrNull(position - 1) ?: return 0..0
            val max = if (prev.node.type != WorkoutNodeType.STEP) 1 else prev.depth
            if (max == 0) return 0..0
            val next = items.getOrNull(position + 1)
            val stillMidGroup = next != null && next.depth >= max
            if (stillMidGroup) return max..max
            if (prev.node.type != WorkoutNodeType.STEP) return max..max
            return 0..max
        }

        /**
         * The previewed depth of the STEP at [position]. An ambiguous position, see
         * [depthRangeAt], stays in the group until the row is dragged down more than half
         * [rowHeightPx]. [dY] is rebased to 0 whenever the adapter position changes.
         */
        fun previewDepthAt(position: Int, dY: Float, rowHeightPx: Int): Int {
            val range = depthRangeAt(position)
            if (range.first == range.last) return range.first
            return if (dY > rowHeightPx / 2f) range.first else range.last
        }

        /**
         * The positions of the group the STEP at [position] joins at [depth]: the header and every
         * row in the group. Null when [depth] is 0.
         *
         * The walk starts from the row above [position], since the stored depth of the dragged row
         * is stale during the drag.
         */
        fun groupRangeAt(position: Int, depth: Int): IntRange? {
            if (depth <= 0) return null
            var headerIndex = position - 1
            while (headerIndex >= 0 && items[headerIndex].depth >= 1) headerIndex--
            if (headerIndex < 0 || items[headerIndex].node.type == WorkoutNodeType.STEP) return null
            var endIndex = headerIndex + 1
            while (endIndex < items.size && (endIndex == position || items[endIndex].depth >= 1)) endIndex++
            return headerIndex until endIndex
        }

        /**
         * Ends the drag. Puts [collapsedChildren] back after the row of [draggedLocalId], sets
         * the row to [draggedDepth], and returns the (node id, depth) of every row for
         * [WorkoutTemplateEditViewModel.applyReorder].
         */
        fun finishDrag(draggedLocalId: Long?, draggedDepth: Int?): List<Pair<Long, Int>> {
            val pending = collapsedChildren
            collapsedChildren = null
            if (pending != null && draggedLocalId != null) {
                val headerIndex = indexOf(draggedLocalId)
                if (headerIndex >= 0) {
                    items.addAll(headerIndex + 1, pending)
                    notifyItemRangeInserted(headerIndex + 1, pending.size)
                }
            }

            if (draggedLocalId != null) {
                val index = indexOf(draggedLocalId)
                val row = items.getOrNull(index)
                if (row != null) {
                    val resolvedDepth = if (row.node.type == WorkoutNodeType.STEP) draggedDepth ?: row.depth else 0
                    if (resolvedDepth != row.depth) items[index] = row.copy(depth = resolvedDepth)
                }
            }

            return items.map { it.node.localId to it.depth }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val indent: View = view.findViewById(R.id.workout_step_indent)
            val colorBar: View = view.findViewById(R.id.workout_step_color_bar)
            val title: TextView = view.findViewById(R.id.workout_step_title)
            val detail: TextView = view.findViewById(R.id.workout_step_detail)
            val warning: ImageView = view.findViewById(R.id.workout_step_warning)
            val overflow: ImageButton = view.findViewById(R.id.workout_step_overflow)
            val dragHandle: View = view.findViewById(R.id.workout_step_drag_handle)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workout_template_step, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = items[position]
            val node = row.node
            val context = holder.itemView.context
            holder.indent.layoutParams = holder.indent.layoutParams.apply { width = dp(context, INDENT_DP) * row.depth }
            // The view may have been recycled from a tinted drop target
            holder.itemView.setBackgroundResource(defaultRowBackgroundResId)

            val childCount = node.children.size
            val childCountText =
                context.resources.getQuantityString(R.plurals.workout_template_step_count, childCount, childCount)
            val colorRes = when (node.type) {
                WorkoutNodeType.STEP -> {
                    val sportSpec = spec.sports[viewModel.template.activityKind]
                    val stepType = node.stepType
                    holder.title.text = when {
                        stepType == WorkoutStepType.ACTIVE && sportSpec != null -> context.getString(sportSpec.activeLabel)
                        stepType != null -> context.getString(stepType.label)
                        else -> context.getString(R.string.other)
                    }
                    holder.detail.text = stepSummary(context, sportSpec, exerciseCatalog, node)
                    stepType?.colorRes ?: R.color.workout_step_color_other
                }

                WorkoutNodeType.REPEAT -> {
                    holder.title.text = context.getString(R.string.workout_template_repeat_title, node.repeatCount ?: 0)
                    holder.detail.text = childCountText
                    R.color.workout_step_color_group
                }

                WorkoutNodeType.LEG -> {
                    holder.title.text = node.legActivityKind?.getLabel(context)
                        ?: context.getString(R.string.workout_template_multisport_leg_title)
                    holder.detail.text = childCountText
                    R.color.workout_step_color_group
                }
            }
            holder.colorBar.setBackgroundColor(ContextCompat.getColor(context, colorRes))

            val issue = issuesByNode[node.localId]
            if (issue != null) {
                holder.warning.visibility = View.VISIBLE
                holder.detail.text = context.getString(issue.message)
                holder.detail.setTextColor(
                    MaterialColors.getColor(
                        context,
                        android.R.attr.colorError,
                        ContextCompat.getColor(context, R.color.workout_step_color_active)
                    )
                )
            } else {
                holder.warning.visibility = View.GONE
                holder.detail.setTextColor(
                    MaterialColors.getColor(
                        context,
                        android.R.attr.textColorSecondary,
                        ContextCompat.getColor(context, R.color.workout_step_color_other)
                    )
                )
            }

            holder.itemView.setOnClickListener {
                when (node.type) {
                    WorkoutNodeType.STEP -> editStep(node)
                    WorkoutNodeType.LEG -> editLeg(node)
                    WorkoutNodeType.REPEAT -> {}
                }
            }
            holder.overflow.setOnClickListener { anchor -> showRowMenu(anchor, row) }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(holder)
                false
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE_ID = "workout_template_id"
        const val EXTRA_ACTIVITY_KIND = "workout_template_activity_kind"
        const val EXTRA_LEG = "workout_template_leg"
        const val RESULT_TEMPLATE_SAVED = 1

        private const val MENU_EDIT = 1
        private const val MENU_ADD_STEP_INSIDE = 2
        private const val MENU_DELETE = 3

        /**
         * The indent of one nesting level of a step row.
         */
        private const val INDENT_DP = 24
    }
}

/**
 * The detail line of a step row: the exercise, the duration, the target and the weight, each
 * with its value when [sportSpec] declares a [ValueSpec] for it.
 */
private fun stepSummary(
    context: Context,
    sportSpec: SportSpec?,
    catalog: WorkoutExerciseCatalog?,
    node: WorkoutStepNode,
): String {
    val stepSpec = node.stepType?.let { sportSpec?.stepSpec(it) }
    val parts = mutableListOf<String>()

    catalog?.byId(node.exerciseId)?.let { parts.add(it.name) }

    node.duration?.let { duration ->
        val valueSpec = stepSpec?.durations?.find { it.type == duration.type }?.value
        parts.add(labeledValue(context, duration.type.label, valueSpec, duration.value))
    }

    node.target?.let { target ->
        if (target.type != WorkoutTargetType.NONE) {
            val valueSpec = stepSpec?.primaryTargets?.find { it.type == target.type }?.value
            val valueText = when {
                target.type.isEnum -> target.enumValue?.let { name -> WorkoutEffort.entries.find { it.name == name } }
                    ?.let { context.getString(it.label) }

                target.type.isZone -> target.zone?.toString()
                target.type.isRange && valueSpec is ValueSpec.PairRange && target.low != null && target.high != null ->
                    formatPairValue(context, valueSpec, target.low, target.high)

                valueSpec != null && target.low != null -> formatScalarValue(context, valueSpec, target.low)
                else -> null
            }
            parts.add(listOfNotNull(context.getString(target.type.label), valueText).joinToString(" "))
        }
    }

    node.weightType?.let { weightType ->
        if (weightType != WorkoutWeightType.NONE) {
            val valueSpec = stepSpec?.fields?.weightSpecs?.get(weightType)
            parts.add(labeledValue(context, weightType.label, valueSpec, node.weightValue?.toLong()))
        }
    }

    return parts.joinToString(" · ")
}

private fun labeledValue(
    context: Context,
    @StringRes label: Int,
    valueSpec: ValueSpec?,
    value: Long?,
): String {
    val valueText = if (valueSpec != null && value != null) formatScalarValue(context, valueSpec, value) else null
    return listOfNotNull(context.getString(label), valueText).joinToString(" ")
}
