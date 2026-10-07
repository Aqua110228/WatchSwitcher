package nodomain.freeyourgadget.gadgetbridge.database.repository

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplateDao
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplateStepDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutMeasurementSystem
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSyncStateStatus
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSyncStatus
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimDrill
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimStroke
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import org.slf4j.LoggerFactory
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplate as WorkoutTemplateRow
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplateStep as WorkoutTemplateStepRow

/**
 * Persistence of [WorkoutTemplate]s and of their sync state.
 */
object WorkoutTemplateRepository {
    private val LOG = LoggerFactory.getLogger(WorkoutTemplateRepository::class.java)

    /**
     * Local broadcast sent when the sync state of a template changes. Carries [EXTRA_TEMPLATE_ID].
     */
    const val ACTION_SYNC_STATE_CHANGED = "nodomain.freeyourgadget.gadgetbridge.workout_template_sync_state_changed"
    const val EXTRA_TEMPLATE_ID = "template_id"

    /**
     * The templates of [device], most recently updated first.
     */
    fun list(device: GBDevice): List<WorkoutTemplate> = try {
        GBApplication.acquireDbReadOnly().use { db ->
            val session = db.daoSession
            val deviceRow = DBHelper.findDevice(device, session)
            if (deviceRow == null) {
                emptyList()
            } else {
                session.workoutTemplateDao.queryBuilder()
                    .where(WorkoutTemplateDao.Properties.DeviceId.eq(deviceRow.id))
                    .orderDesc(WorkoutTemplateDao.Properties.UpdatedAt)
                    .list()
                    .map { toTemplate(it, session) }
            }
        }
    } catch (e: Exception) {
        LOG.error("Failed to list workout templates of device {}", device.address, e)
        emptyList()
    }

    fun load(id: Long): WorkoutTemplate? = try {
        GBApplication.acquireDbReadOnly().use { db ->
            val session = db.daoSession
            session.workoutTemplateDao.load(id)?.let { toTemplate(it, session) }
        }
    } catch (e: Exception) {
        LOG.error("Failed to load workout template {}", id, e)
        null
    }

    /**
     * Inserts or updates [template] for [device] and rewrites its step rows. Returns the template
     * with its id set, or null if it failed to be written.
     */
    fun save(template: WorkoutTemplate, device: GBDevice): WorkoutTemplate? = try {
        GBApplication.acquireDB().use { db ->
            val deviceId = requireNotNull(DBHelper.getDevice(device, db.daoSession).id)
            write(db, template, deviceId)
        }
    } catch (e: Exception) {
        LOG.error("Failed to save workout template {}", template.id, e)
        null
    }

    /**
     * Writes [template] and its steps as rows of [deviceId]. The sync columns are left as they
     * are, since only a sync changes them.
     */
    private fun write(db: DBHandler, template: WorkoutTemplate, deviceId: Long): WorkoutTemplate {
        val session = db.daoSession
        db.database.beginTransaction()
        try {
            val row = template.id?.let { session.workoutTemplateDao.load(it) } ?: WorkoutTemplateRow()
            row.deviceId = deviceId
            row.vendorId = template.vendorId
            row.name = template.name
            row.note = template.note
            row.activityKind = template.activityKind.code
            row.createdAt = template.createdAt
            row.updatedAt = System.currentTimeMillis()
            row.poolLength = template.poolLength
            row.poolLengthUnit = template.poolLengthUnit?.name
            row.transitions = template.transitions
            session.workoutTemplateDao.insertOrReplace(row)
            val templateId = requireNotNull(row.id) { "WorkoutTemplate row ID should not be null after inserting it" }

            deleteSteps(session, templateId)
            insertSteps(session, templateId, template.steps, parentId = null)

            db.database.setTransactionSuccessful()
            return template.copy(id = templateId, updatedAt = row.updatedAt)
        } finally {
            db.database.endTransaction()
        }
    }

    private fun insertSteps(session: DaoSession, templateId: Long, nodes: List<WorkoutStepNode>, parentId: Long?) {
        var sortOrder = 0
        for (node in nodes) {
            val row = WorkoutTemplateStepRow()
            row.templateId = templateId
            row.parentId = parentId
            row.sortOrder = sortOrder++
            row.nodeType = node.type.name
            row.stepType = node.stepType?.name
            row.note = node.note
            row.repeatCount = node.repeatCount
            row.legActivityKind = node.legActivityKind?.code
            row.durationType = node.duration?.type?.name
            row.durationValue = node.duration?.value
            row.targetType = node.target?.type?.name
            row.targetZone = node.target?.zone
            row.targetLow = node.target?.low
            row.targetHigh = node.target?.high
            row.targetEnum = node.target?.enumValue
            row.secondaryTargetType = node.secondaryTarget?.type?.name
            row.secondaryTargetZone = node.secondaryTarget?.zone
            row.secondaryTargetLow = node.secondaryTarget?.low
            row.secondaryTargetHigh = node.secondaryTarget?.high
            row.secondaryTargetEnum = node.secondaryTarget?.enumValue
            row.exerciseId = node.exerciseId
            row.weightType = node.weightType?.name
            row.weightValue = node.weightValue
            row.swimStroke = node.swimStroke?.name
            row.swimDrill = node.swimDrill?.name
            row.swimEquipment = node.swimEquipment?.name
            row.measurementSystem = node.measurementSystem?.name
            session.workoutTemplateStepDao.insert(row)

            if (node.children.isNotEmpty()) {
                insertSteps(session, templateId, node.children, parentId = requireNotNull(row.id))
            }
        }
    }

    fun delete(id: Long) {
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                deleteSteps(session, id)
                session.workoutTemplateDao.deleteByKey(id)
            }
        } catch (e: Exception) {
            LOG.error("Failed to delete workout template {}", id, e)
        }
    }

    /**
     * Deletes every template of the device with [deviceId], with their steps.
     */
    fun deleteByDevice(session: DaoSession, deviceId: Long) {
        val rows = session.workoutTemplateDao.queryBuilder()
            .where(WorkoutTemplateDao.Properties.DeviceId.eq(deviceId))
            .list()
        for (row in rows) {
            deleteSteps(session, row.id!!)
            session.workoutTemplateDao.delete(row)
        }
    }

    private fun deleteSteps(session: DaoSession, templateId: Long) {
        session.workoutTemplateStepDao.queryBuilder()
            .where(WorkoutTemplateStepDao.Properties.TemplateId.eq(templateId))
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
    }

    /**
     * Saves a copy of template [id], named [newName], on the same device.
     */
    fun duplicate(id: Long, newName: String): WorkoutTemplate? = try {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val row = session.workoutTemplateDao.load(id)
            if (row == null) {
                null
            } else {
                val original = toTemplate(row, session)
                val copy = original.copy(
                    id = null,
                    name = newName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = null,
                    syncContentHash = null,
                    syncRemoteId = null,
                    syncError = null,
                    steps = deepCopy(original.steps),
                )
                write(db, copy, row.deviceId)
            }
        }
    } catch (e: Exception) {
        LOG.error("Failed to duplicate workout template {}", id, e)
        null
    }

    private fun deepCopy(nodes: List<WorkoutStepNode>): MutableList<WorkoutStepNode> =
        nodes.mapTo(mutableListOf()) {
            it.copy(
                localId = WorkoutStepNode.nextLocalId(),
                children = deepCopy(it.children)
            )
        }

    /**
     * A hash of every field of [template] that is sent to a device. The id and the timestamps are
     * not included.
     */
    fun contentHash(template: WorkoutTemplate): Int = buildString {
        append(template.name).append('|')
        append(template.note).append('|')
        append(template.activityKind.code).append('|')
        append(template.poolLength).append('|')
        append(template.poolLengthUnit?.name).append('|')
        append(template.transitions).append('|')
        template.steps.forEach { appendNode(it) }
    }.hashCode()

    private fun StringBuilder.appendNode(node: WorkoutStepNode) {
        append("[node").append(node.type.name)
            .append(',').append(node.stepType?.name)
            .append(',').append(node.note)
            .append(',').append(node.repeatCount)
            .append(',').append(node.legActivityKind?.code)
            .append(',').append(node.duration?.type?.name).append(':').append(node.duration?.value)
            .append(',').append(node.target?.type?.name).append(':').append(node.target?.zone).append(':')
            .append(node.target?.low).append(':').append(node.target?.high).append(':').append(node.target?.enumValue)
            .append(',').append(node.secondaryTarget?.type?.name).append(':').append(node.secondaryTarget?.zone)
            .append(':').append(node.secondaryTarget?.low).append(':').append(node.secondaryTarget?.high).append(':')
            .append(node.secondaryTarget?.enumValue)
            .append(',').append(node.exerciseId)
            .append(',').append(node.weightType?.name).append(':').append(node.weightValue)
            .append(',').append(node.swimStroke?.name).append(':').append(node.swimDrill?.name).append(':')
            .append(node.swimEquipment?.name)
            .append(',').append(node.measurementSystem?.name)
            .append(']')
        node.children.forEach { appendNode(it) }
    }

    /**
     * The status of [template] on [device].
     */
    fun syncStatus(template: WorkoutTemplate, device: GBDevice): WorkoutSyncStatus =
        when (template.syncStatus) {
            // A PENDING template on a disconnected device is an upload that never finished
            WorkoutSyncStateStatus.PENDING ->
                if (device.isInitialized) WorkoutSyncStatus.IN_PROGRESS else WorkoutSyncStatus.FAILED

            WorkoutSyncStateStatus.FAILED -> WorkoutSyncStatus.FAILED
            WorkoutSyncStateStatus.SUCCESS ->
                if (template.syncContentHash == contentHash(template)) {
                    WorkoutSyncStatus.SYNCED
                } else {
                    WorkoutSyncStatus.OUT_OF_DATE
                }

            null -> WorkoutSyncStatus.NOT_SYNCED
        }

    /**
     * Marks a template as being sent to its device. Keeps the [WorkoutTemplate.syncRemoteId].
     */
    fun markSyncPending(templateId: Long) {
        updateSyncState(templateId) { row ->
            row.syncStatus = WorkoutSyncStateStatus.PENDING.name
            row.syncError = null
        }
    }

    fun markSynced(templateId: Long, remoteId: String?, contentHash: Int) {
        updateSyncState(templateId) { row ->
            row.syncStatus = WorkoutSyncStateStatus.SUCCESS.name
            row.syncContentHash = contentHash
            row.syncRemoteId = remoteId
            row.syncError = null
        }
    }

    fun markSyncFailed(templateId: Long, reason: String?) {
        updateSyncState(templateId) { row ->
            row.syncStatus = WorkoutSyncStateStatus.FAILED.name
            row.syncError = reason
        }
    }

    /**
     * Applies [block] to the sync columns of template [templateId], saves it and broadcasts
     * [ACTION_SYNC_STATE_CHANGED].
     */
    private fun updateSyncState(templateId: Long, block: (WorkoutTemplateRow) -> Unit) {
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val row = session.workoutTemplateDao.load(templateId)
                if (row == null) {
                    LOG.error("Workout template {} not found", templateId)
                    return
                }
                block(row)
                session.workoutTemplateDao.update(row)
            }
        } catch (e: Exception) {
            LOG.error("Failed to save sync state of template {}", templateId, e)
            return
        }

        LocalBroadcastManager.getInstance(GBApplication.getContext()).sendBroadcast(
            Intent(ACTION_SYNC_STATE_CHANGED).putExtra(EXTRA_TEMPLATE_ID, templateId)
        )
    }

    private fun toTemplate(row: WorkoutTemplateRow, session: DaoSession): WorkoutTemplate {
        val rows = session.workoutTemplateStepDao.queryBuilder()
            .where(WorkoutTemplateStepDao.Properties.TemplateId.eq(row.id))
            .orderAsc(WorkoutTemplateStepDao.Properties.SortOrder)
            .list()
        val nodesById = rows.associate { it.id to toNode(it) }
        val topLevel = mutableListOf<WorkoutStepNode>()
        for (stepRow in rows) {
            val node = nodesById.getValue(stepRow.id)
            val parent = stepRow.parentId?.let { nodesById[it] }
            if (parent != null) parent.children.add(node) else topLevel.add(node)
        }
        return WorkoutTemplate(
            id = row.id,
            vendorId = row.vendorId,
            name = row.name,
            note = row.note,
            activityKind = ActivityKind.fromCode(row.activityKind),
            poolLength = row.poolLength,
            poolLengthUnit = WorkoutPoolLengthUnit.fromName(row.poolLengthUnit),
            transitions = row.transitions,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            syncStatus = WorkoutSyncStateStatus.fromName(row.syncStatus),
            syncContentHash = row.syncContentHash,
            syncRemoteId = row.syncRemoteId,
            syncError = row.syncError,
            steps = topLevel,
        )
    }

    private fun toNode(row: WorkoutTemplateStepRow): WorkoutStepNode = WorkoutStepNode(
        localId = requireNotNull(row.id),
        type = WorkoutNodeType.fromName(row.nodeType) ?: WorkoutNodeType.STEP,
        stepType = WorkoutStepType.fromName(row.stepType),
        note = row.note,
        repeatCount = row.repeatCount,
        legActivityKind = row.legActivityKind?.let {
            ActivityKind.fromCode(it)
        },
        duration = WorkoutDurationType.fromName(row.durationType)?.let {
            WorkoutDuration(it, row.durationValue)
        },
        target = WorkoutTargetType.fromName(row.targetType)?.let {
            WorkoutTarget(it, row.targetZone, row.targetLow, row.targetHigh, row.targetEnum)
        },
        secondaryTarget = WorkoutTargetType.fromName(row.secondaryTargetType)?.let {
            WorkoutTarget(
                it,
                row.secondaryTargetZone,
                row.secondaryTargetLow,
                row.secondaryTargetHigh,
                row.secondaryTargetEnum
            )
        },
        exerciseId = row.exerciseId,
        weightType = WorkoutWeightType.fromName(row.weightType),
        weightValue = row.weightValue,
        swimStroke = WorkoutSwimStroke.fromName(row.swimStroke),
        swimDrill = WorkoutSwimDrill.fromName(row.swimDrill),
        swimEquipment = WorkoutEquipment.fromName(row.swimEquipment),
        measurementSystem = WorkoutMeasurementSystem.fromName(row.measurementSystem),
        children = mutableListOf(),
    )
}
