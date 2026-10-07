package nodomain.freeyourgadget.gadgetbridge.model.workouts.spec

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The display unit of a [ValueSpec.Range]. [displayScale] is the number of stored units in one
 * display unit.
 *
 * TODO: Display is metric-only for now.
 */
enum class WorkoutUnit(val displayScale: Double, val decimals: Int, @StringRes val suffixLabel: Int?) {
    DISTANCE_KM(100_000.0, 2, R.string.km),
    DISTANCE_M(100.0, 1, R.string.meters),
    CALORIES(1.0, 0, R.string.calories_unit),
    HEART_RATE(1.0, 0, R.string.bpm),
    POWER(1.0, 0, R.string.workout_unit_suffix_watt),
    COUNT(1.0, 0, null),
    SPEED_KMH(1000.0 / 3.6, 1, R.string.km_h),
    CADENCE(1.0, 0, R.string.workout_unit_suffix_steps_per_minute),
    STROKE_RATE(1.0, 0, R.string.workout_unit_suffix_steps_per_minute),
    PERCENT(1.0, 0, R.string.unit_percentage),
    WEIGHT_KG(1000.0, 1, R.string.unit_kg),
    TIME_OFFSET_SECONDS(1.0, 0, R.string.workout_unit_suffix_seconds),
    ;

    fun toDisplay(stored: Long): Double = stored / displayScale
    fun toStored(display: Double): Long = (display * displayScale).roundToLong()
}

/**
 * The permitted values of one step field. The editor selects the picker by subclass.
 */
sealed class ValueSpec {
    /**
     * An integer from [min] to [max], in increments of [step], in a fixed [unit]. [default] is the
     * picker default, [min] when null.
     */
    data class Range(
        val min: Long,
        val max: Long,
        val step: Long,
        val unit: WorkoutUnit,
        val default: Long? = null,
    ) : ValueSpec()

    /**
     * A duration in milliseconds, edited as h:mm:ss. [default] is the picker default, [min] when
     * null.
     */
    data class Time(
        val min: Long,
        val max: Long,
        val step: Long,
        val default: Long? = null,
    ) : ValueSpec()

    /**
     * A pace in milliseconds per [basisMeters], edited as mm:ss.
     */
    data class Pace(
        val min: Long,
        val max: Long,
        val basisMeters: Int = 1000,
    ) : ValueSpec()

    /**
     * A zone number from [min] to [max]. [labels] gives each zone a display name. It is either
     * empty or has exactly one entry per zone. [default] is the picker default, [min] when null.
     */
    data class Zone(
        val min: Int,
        val max: Int,
        val labels: List<Int> = emptyList(),
        val default: Int? = null,
    ) : ValueSpec()

    /**
     * One value of a labeled set.
     */
    data class EnumValues<T : LabeledEntry>(
        val values: List<T>,
    ) : ValueSpec()

    /**
     * A low/high pair of [inner]. [defaultLow] and [defaultHigh] are the picker defaults, in the
     * stored unit of [inner].
     */
    data class PairRange(
        val inner: ValueSpec,
        val defaultLow: Long? = null,
        val defaultHigh: Long? = null,
    ) : ValueSpec()
}

private const val DEFAULT_DISTANCE_KM = 1.0
private const val DEFAULT_CALORIES = 50
private const val DEFAULT_POWER_WATTS = 250
private val DEFAULT_TIME = 20.minutes
private const val DEFAULT_HR_ZONE = 3

fun distanceKm(min: Double, max: Double, step: Double, default: Double = DEFAULT_DISTANCE_KM): ValueSpec.Range =
    ValueSpec.Range(
        WorkoutUnit.DISTANCE_KM.toStored(min),
        WorkoutUnit.DISTANCE_KM.toStored(max),
        WorkoutUnit.DISTANCE_KM.toStored(step),
        WorkoutUnit.DISTANCE_KM,
        WorkoutUnit.DISTANCE_KM.toStored(default),
    )

fun distanceM(min: Double, max: Double, step: Double): ValueSpec.Range = ValueSpec.Range(
    WorkoutUnit.DISTANCE_M.toStored(min),
    WorkoutUnit.DISTANCE_M.toStored(max),
    WorkoutUnit.DISTANCE_M.toStored(step),
    WorkoutUnit.DISTANCE_M
)

fun time(min: Duration, max: Duration, step: Duration, default: Duration = DEFAULT_TIME): ValueSpec.Time =
    ValueSpec.Time(min.inWholeMilliseconds, max.inWholeMilliseconds, step.inWholeMilliseconds, default.inWholeMilliseconds)

fun pace(min: Duration, max: Duration): ValueSpec.Pace =
    ValueSpec.Pace(min.inWholeMilliseconds, max.inWholeMilliseconds, basisMeters = 1000)

/**
 * A pool swim pace, per 100 m.
 */
fun swimPace(min: Duration, max: Duration): ValueSpec.Pace =
    ValueSpec.Pace(min.inWholeMilliseconds, max.inWholeMilliseconds, basisMeters = 100)

fun calories(min: Int, max: Int, step: Int = 1, default: Int = DEFAULT_CALORIES): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), step.toLong(), WorkoutUnit.CALORIES, default.toLong())

fun heartRate(min: Int, max: Int, default: Int? = null): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), 1, WorkoutUnit.HEART_RATE, default?.toLong())

fun power(min: Int, max: Int, step: Int = 1, default: Int = DEFAULT_POWER_WATTS): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), step.toLong(), WorkoutUnit.POWER, default.toLong())

fun count(min: Int, max: Int, step: Int = 1): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), step.toLong(), WorkoutUnit.COUNT)

fun speedKmh(min: Double, max: Double, step: Double = 0.1): ValueSpec.Range = ValueSpec.Range(
    WorkoutUnit.SPEED_KMH.toStored(min),
    WorkoutUnit.SPEED_KMH.toStored(max),
    maxOf(1, WorkoutUnit.SPEED_KMH.toStored(step)),
    WorkoutUnit.SPEED_KMH
)

fun cadence(min: Int, max: Int): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), 1, WorkoutUnit.CADENCE)

fun strokeRate(min: Int, max: Int): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), 1, WorkoutUnit.STROKE_RATE)

fun percent(min: Int, max: Int, step: Int = 1): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), step.toLong(), WorkoutUnit.PERCENT)

fun weightKg(min: Double, max: Double, step: Double): ValueSpec.Range = ValueSpec.Range(
    WorkoutUnit.WEIGHT_KG.toStored(min),
    WorkoutUnit.WEIGHT_KG.toStored(max),
    WorkoutUnit.WEIGHT_KG.toStored(step),
    WorkoutUnit.WEIGHT_KG
)

fun secondsOffset(min: Int, max: Int): ValueSpec.Range =
    ValueSpec.Range(min.toLong(), max.toLong(), 1, WorkoutUnit.TIME_OFFSET_SECONDS, default = 0)

fun zone(min: Int, max: Int): ValueSpec.Zone = ValueSpec.Zone(min, max)

/**
 * The 5 labeled heart rate zones.
 */
fun heartRateZone(default: Int = DEFAULT_HR_ZONE): ValueSpec.Zone = ValueSpec.Zone(
    1, 5,
    listOf(
        R.string.workout_hr_zone_1,
        R.string.workout_hr_zone_2,
        R.string.workout_hr_zone_3,
        R.string.workout_hr_zone_4,
        R.string.workout_hr_zone_5,
    ),
    default,
)

/**
 * The 7 power zones.
 */
fun powerZone(): ValueSpec.Zone = ValueSpec.Zone(
    1, 7,
    listOf(
        R.string.workout_power_zone_1,
        R.string.workout_power_zone_2,
        R.string.workout_power_zone_3,
        R.string.workout_power_zone_4,
        R.string.workout_power_zone_5,
        R.string.workout_power_zone_6,
        R.string.workout_power_zone_7,
    ),
)

fun <T : LabeledEntry> enumOf(values: List<T>): ValueSpec.EnumValues<T> = ValueSpec.EnumValues(values)

fun range(inner: ValueSpec): ValueSpec.PairRange = ValueSpec.PairRange(inner)

/**
 * [low] and [high] are the picker defaults, in the display unit of [inner].
 */
fun range(inner: ValueSpec.Range, low: Number, high: Number): ValueSpec.PairRange =
    ValueSpec.PairRange(inner, inner.unit.toStored(low.toDouble()), inner.unit.toStored(high.toDouble()))

/**
 * [low] and [high] are the picker defaults.
 */
fun range(inner: ValueSpec.Pace, low: Duration, high: Duration): ValueSpec.PairRange =
    ValueSpec.PairRange(inner, low.inWholeMilliseconds, high.inWholeMilliseconds)
