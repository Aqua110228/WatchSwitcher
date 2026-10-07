package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesUtils
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong

/**
 * Encodes a [WorkoutTemplate] into a Zepp OS training-template JSON payload.
 */
object ZeppOsWorkoutTemplateEncoder {
    private val LOG = LoggerFactory.getLogger(ZeppOsWorkoutTemplateEncoder::class.java)

    /**
     * @param templateId the id of the template on the watch. The Zepp app uses the Unix epoch in
     * microseconds.
     */
    fun encode(template: WorkoutTemplate, templateId: Long): JSONObject? {
        val sportType = ZeppOsWorkoutCodes.sportType(template.activityKind)
        if (sportType == null) {
            LOG.error("No Zepp OS sport mapping for {}", template.activityKind)
            return null
        }

        val maxHeartRate = HeartRateZonesUtils.MAXIMUM_HEART_RATE - ActivityUser().age

        val intervals = JSONArray()
        var index = 0
        var groupId = 0

        fun encodeInterval(node: WorkoutStepNode, thisGroupId: Int, loopTimes: Int) {
            val obj = JSONObject()
            obj.put("index", index)
            obj.put("trainingType", ZeppOsWorkoutCodes.trainingType(node.stepType))
            encodeDuration(obj, node.duration, template.activityKind)
            encodeAlert(obj, node.target, maxHeartRate)
            obj.put("groupId", thisGroupId)
            obj.put("loopTimes", loopTimes)
            obj.put("lengthUnit", ZeppOsWorkoutCodes.measurementSystemCode(node.measurementSystem))
            obj.put("intervalDesc", node.note.orEmpty())
            encodeExerciseAndWeight(obj, node)
            intervals.put(obj)
            index++
        }

        for (node in template.steps) {
            when (node.type) {
                WorkoutNodeType.STEP -> {
                    encodeInterval(node, groupId, 1)
                    groupId++
                }

                WorkoutNodeType.REPEAT -> {
                    val count = node.repeatCount ?: 1
                    node.children.forEach { encodeInterval(it, groupId, count) }
                    groupId++
                }

                WorkoutNodeType.LEG -> throw IllegalStateException("Workout legs are not supported by Zepp OS")
            }
        }

        return JSONObject().apply {
            put("sportType", sportType)
            put("templateId", templateId)
            put("templateName", template.name)
            put("intervals", intervals)
        }
    }

    private fun encodeDuration(obj: JSONObject, duration: WorkoutDuration?, activityKind: ActivityKind) {
        val type = duration?.type
        val code = type?.let { ZeppOsWorkoutCodes.durationTypeCode(it, activityKind) }
        if (type == null || code == null || type == WorkoutDurationType.BUTTON_PRESS) {
            if (type != null && code == null) {
                LOG.warn("Duration type {} is not supported by Zepp OS", type)
            }
            obj.put("intervalType", ZeppOsWorkoutCodes.DURATION_OPEN_OR_REPS)
            obj.put("intervalVal", ZeppOsWorkoutCodes.SKIP_BUTTON_VALUE)
            return
        }

        obj.put("intervalType", code)
        val value = duration.value ?: 0L
        val raw = when (type) {
            WorkoutDurationType.DISTANCE -> value / 100 // centimeters -> meters
            WorkoutDurationType.TIME -> value / 1000 // milliseconds -> seconds
            else -> value
        }
        obj.put("intervalVal", raw)
    }

    private fun encodeAlert(obj: JSONObject, target: WorkoutTarget?, maxHeartRate: Int) {
        val t = target ?: WorkoutTarget.NONE

        var remindType = ZeppOsWorkoutCodes.REMIND_OFF
        var up = 0L
        var down = 0L

        when (t.type) {
            WorkoutTargetType.NONE -> {}
            WorkoutTargetType.PACE -> {
                remindType = ZeppOsWorkoutCodes.REMIND_PACE
                up = msToS(t.high)
                down = msToS(t.low)
            }

            WorkoutTargetType.CADENCE -> {
                remindType = ZeppOsWorkoutCodes.REMIND_CADENCE
                up = t.high ?: 0L
                down = t.low ?: 0L
            }

            WorkoutTargetType.SPEED -> {
                remindType = ZeppOsWorkoutCodes.REMIND_SPEED
                up = mmPerSToKmh(t.high)
                down = mmPerSToKmh(t.low)
            }

            WorkoutTargetType.STROKE_RATE -> {
                remindType = ZeppOsWorkoutCodes.REMIND_STROKE_RATE
                up = t.high ?: 0L
                down = t.low ?: 0L
            }

            WorkoutTargetType.HR_RANGE -> {
                remindType = ZeppOsWorkoutCodes.REMIND_HEART_RATE
                up = t.high ?: 0L
                down = t.low ?: 0L
            }

            WorkoutTargetType.HR_ZONE -> {
                val bounds = t.zone?.let { ZeppOsWorkoutCodes.heartRateZoneBounds(it, maxHeartRate) }
                if (bounds != null) {
                    remindType = ZeppOsWorkoutCodes.REMIND_HEART_RATE
                    up = bounds.last.toLong()
                    down = bounds.first.toLong()
                } else {
                    LOG.warn("Heart rate zone bounds for {} are null, disabling the alert", t.zone)
                }
            }

            else -> {
                LOG.warn("Target type {} is not supported by Zepp OS", t.type)
            }
        }

        obj.put("remindType", remindType)
        obj.put("remindUpThreshold", up)
        obj.put("remindDownThreshold", down)
    }

    private fun msToS(ms: Long?): Long = ((ms ?: 0L) / 1000.0).roundToLong()

    private fun mmPerSToKmh(mmPerS: Long?): Long = ((mmPerS ?: 0L) * 3.6 / 1000.0).roundToLong()

    private fun encodeExerciseAndWeight(obj: JSONObject, node: WorkoutStepNode) {
        val exerciseId = node.exerciseId
        val exercise = ZeppOsExerciseCatalog.INSTANCE.exerciseFor(exerciseId)
        if (exerciseId != null && exercise == null) {
            // FIXME what will happen here?
            LOG.warn("No Zepp OS exercise {}, omitting it from the encoded interval", exerciseId)
        }
        obj.put("actionType", exercise?.actionType ?: 0)
        obj.put("actionName", exercise?.actionName.orEmpty())
        obj.put("mainPositions", JSONArray(exercise?.mainPositions ?: emptyList<Int>()))
        obj.put("subPositions", JSONArray(exercise?.subPositions ?: emptyList<Int>()))
        // Only seen set on one capture, Dynamic Plank, so its meaning is unknown
        obj.put("selfWeightType", 0)

        val mode = node.weightType?.let { ZeppOsWorkoutCodes.weightMode(it) }
        val raw = when (node.weightType) {
            WorkoutWeightType.MANUAL -> ZeppOsWorkoutCodes.manualWeightRaw(node.weightValue ?: 0)
            WorkoutWeightType.RM -> ZeppOsWorkoutCodes.rmWeightRaw(node.weightValue ?: 0)
            WorkoutWeightType.BODY_WEIGHT -> 0L
            else -> null
        }
        obj.put("strengthWeight", if (mode != null && raw != null) "$raw-$mode" else "0-0")
    }
}
