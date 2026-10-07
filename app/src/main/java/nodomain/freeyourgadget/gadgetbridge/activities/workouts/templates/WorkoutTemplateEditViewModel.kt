package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.database.repository.WorkoutTemplateRepository
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateValidator
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutValidationIssue

/**
 * One row of the flattened step tree. [contentKey] is the hash of [node] when the row was built,
 * so a `MutableStateFlow` sees an in-place edit of the node as a change.
 */
data class WorkoutStepRow(val node: WorkoutStepNode, val depth: Int, val contentKey: Int = node.hashCode())

/**
 * The displayed rows: every top-level node, with the children of a REPEAT under it. A LEG is one
 * row, without its steps.
 */
private fun WorkoutTemplate.flattenForDisplay(): List<WorkoutStepRow> {
    fun walk(nodes: List<WorkoutStepNode>, depth: Int): List<WorkoutStepRow> =
        nodes.flatMap { n ->
            val children = if (n.type == WorkoutNodeType.LEG) emptyList() else walk(n.children, depth + 1)
            listOf(WorkoutStepRow(n, depth)) + children
        }
    return walk(steps, 0)
}

/**
 * The [WorkoutTemplate] being created or edited. The activity mutates the template in place and
 * calls [refreshRows] after a change.
 *
 * A multisport leg is edited as a template of its own sport, see [startLeg] and [legResult].
 */
class WorkoutTemplateEditViewModel : ViewModel() {
    lateinit var template: WorkoutTemplate
        private set

    /**
     * The leg being edited, or null when editing a whole template.
     */
    var leg: WorkoutStepNode? = null
        private set

    private val _rows = MutableStateFlow<List<WorkoutStepRow>>(emptyList())
    val rows: StateFlow<List<WorkoutStepRow>> = _rows.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _loaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loaded: SharedFlow<Unit> = _loaded.asSharedFlow()

    private val _saveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveFailed: SharedFlow<Unit> = _saveFailed.asSharedFlow()

    /**
     * Whether [startNew], [startLeg] or [load] was called.
     */
    var started: Boolean = false
        private set

    /**
     * Whether [template] is set. False while [load] is still reading it.
     */
    val hasTemplate: Boolean
        get() = ::template.isInitialized

    /**
     * The [WorkoutTemplateRepository.contentHash] of [template] as of the last [markBaseline] call.
     */
    private var baselineHash: Int = 0

    fun startNew(vendorId: String, activityKind: ActivityKind) {
        started = true
        template = WorkoutTemplate(vendorId = vendorId, activityKind = activityKind)
        refreshRows()
    }

    /**
     * Starts editing the steps of [legNode] as a template of the leg's sport.
     */
    fun startLeg(vendorId: String, legNode: WorkoutStepNode) {
        started = true
        leg = legNode
        template = WorkoutTemplate(
            vendorId = vendorId,
            activityKind = requireNotNull(legNode.legActivityKind) { "A leg needs a sport" },
            steps = legNode.children.toMutableList(),
        )
        refreshRows()
    }

    /**
     * The leg of [startLeg], with the edited steps.
     */
    fun legResult(): WorkoutStepNode = requireNotNull(leg).copy(children = template.steps)

    fun load(templateId: Long) {
        started = true
        viewModelScope.launch {
            val loadedTemplate =
                withContext(Dispatchers.IO) { WorkoutTemplateRepository.load(templateId) } ?: return@launch
            template = loadedTemplate
            refreshRows()
            _loaded.tryEmit(Unit)
        }
    }

    /**
     * Records the current content of [template] as the saved state, for [hasUnsavedChanges].
     */
    fun markBaseline() {
        baselineHash = WorkoutTemplateRepository.contentHash(template)
    }

    fun hasUnsavedChanges(): Boolean = WorkoutTemplateRepository.contentHash(template) != baselineHash

    fun refreshRows() {
        _rows.value = template.flattenForDisplay()
    }

    /**
     * Appends [node] at the top level.
     */
    fun addTopLevel(node: WorkoutStepNode) {
        template.steps.add(node)
        refreshRows()
    }

    /**
     * Appends [node] to the children of [parent].
     */
    fun addChild(parent: WorkoutStepNode, node: WorkoutStepNode) {
        parent.children.add(node)
        refreshRows()
    }

    fun remove(node: WorkoutStepNode) {
        containerOf(node)?.removeAll { it.localId == node.localId }
        refreshRows()
    }

    /**
     * Replaces the node with the [WorkoutStepNode.localId] of [edited] with [edited].
     */
    fun replaceNode(edited: WorkoutStepNode) {
        val container = containerOf(edited) ?: return
        val index = container.indexOfFirst { it.localId == edited.localId }
        if (index < 0) return
        container[index] = edited
        refreshRows()
    }

    /**
     * Rebuilds the step tree from [order], the (node id, depth) of every displayed row after a
     * drag. A depth-0 entry is top-level. A deeper STEP becomes the last child of the most recent
     * top-level REPEAT. The steps of a LEG are not displayed and stay as they are.
     */
    fun applyReorder(order: List<Pair<Long, Int>>) {
        val byId = _rows.value.associate { it.node.localId to it.node }
        byId.values.forEach { if (it.type == WorkoutNodeType.REPEAT) it.children.clear() }

        val newTop = mutableListOf<WorkoutStepNode>()
        var currentGroup: WorkoutStepNode? = null
        for ((localId, depth) in order) {
            val node = byId[localId] ?: continue
            val group = currentGroup
            if (depth > 0 && node.type == WorkoutNodeType.STEP && group != null) {
                group.children.add(node)
            } else {
                newTop.add(node)
                currentGroup = node.takeIf { it.type == WorkoutNodeType.REPEAT }
            }
        }
        template.steps = newTop
        refreshRows()
    }

    /**
     * The list of siblings that contains [node].
     */
    private fun containerOf(node: WorkoutStepNode): MutableList<WorkoutStepNode>? {
        fun search(list: MutableList<WorkoutStepNode>): MutableList<WorkoutStepNode>? {
            if (list.any { it.localId == node.localId }) return list
            for (n in list) {
                search(n.children)?.let { return it }
            }
            return null
        }
        return search(template.steps)
    }

    /**
     * The validation issues of the displayed rows. An issue inside a LEG is reported on the leg.
     */
    fun validate(spec: WorkoutTemplateSpec): List<WorkoutValidationIssue> {
        if (!hasTemplate) return emptyList()
        if (leg != null) return WorkoutTemplateValidator.validateNodes(template.steps, spec, template.activityKind)

        val issues = WorkoutTemplateValidator.validate(template, spec)
        val legOfNode = HashMap<Long, Long>()
        for (node in template.steps) {
            if (node.type != WorkoutNodeType.LEG) continue
            node.flattenSelfAndDescendants().forEach { legOfNode[it.localId] = node.localId }
        }
        if (legOfNode.isEmpty()) return issues
        return issues.map { issue ->
            val legId = issue.nodeLocalId?.let { legOfNode[it] }
            if (legId == null) issue else issue.copy(nodeLocalId = legId)
        }
    }

    fun save(device: GBDevice) {
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) { WorkoutTemplateRepository.save(template, device) }
            if (written == null) {
                _saveFailed.tryEmit(Unit)
                return@launch
            }
            template = written
            markBaseline()
            _saved.tryEmit(Unit)
        }
    }
}
