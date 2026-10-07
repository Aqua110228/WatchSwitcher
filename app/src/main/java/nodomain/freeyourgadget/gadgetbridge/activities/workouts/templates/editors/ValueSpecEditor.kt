package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors

import android.content.Context
import android.content.DialogInterface
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.ValueSpec
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A field bound to one scalar [ValueSpec]. [view] is the field. [value] is the stored value,
 * clamped to the spec, or null before the user picks one.
 */
class ScalarValueEditor(
    val view: View,
    private val getter: () -> Long?,
    private val setter: (Long?) -> Unit,
) {
    var value: Long?
        get() = getter()
        set(v) {
            setter(v)
        }
}

/**
 * A field bound to one [LabeledEntry] value. [view] is the field.
 */
class ChoiceRowEditor<T : LabeledEntry>(
    val view: View,
    private val getter: () -> T?,
    private val setter: (T?) -> Unit,
) {
    var value: T?
        get() = getter()
        set(v) {
            setter(v)
        }
}

private fun marginParams(context: Context) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(context, 8)
    }

internal fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).roundToLong().toInt()

/**
 * Hides the soft keyboard. A dialog or popup menu over the same window does not hide it on its
 * own.
 */
internal fun hideKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

/**
 * A small error-colored [TextView], hidden until [setFieldError] sets a message.
 */
internal fun errorTextView(context: Context): TextView {
    val view = TextView(context)
    view.setTextColor(MaterialColors.getColor(context, android.R.attr.colorError, 0xFFB00020.toInt()))
    view.textSize = 12f
    view.visibility = View.GONE
    return view
}

/**
 * Shows [message] on [errorText], or hides it when null.
 */
internal fun setFieldError(errorText: TextView, @StringRes message: Int?) {
    if (message == null) {
        errorText.visibility = View.GONE
    } else {
        errorText.text = errorText.context.getString(message)
        errorText.visibility = View.VISIBLE
    }
}

/**
 * A non-editable outlined field that displays a value as text. The caller attaches the click
 * listener.
 */
internal fun tapField(context: Context, hint: CharSequence): Pair<TextInputLayout, TextInputEditText> {
    val layout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)
    layout.layoutParams = marginParams(context)
    layout.hint = hint
    val field = TextInputEditText(layout.context)
    field.layoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    field.isFocusable = false
    field.isCursorVisible = false
    field.keyListener = null
    layout.addView(field)
    return layout to field
}

/**
 * Shows [content] centered in a dialog with OK and Cancel buttons. [onCancel] runs on every way
 * out other than OK.
 */
private fun showPickerDialog(
    context: Context,
    title: CharSequence,
    content: View,
    onCancel: () -> Unit = {},
    onConfirm: () -> Unit
): AlertDialog {
    val container = FrameLayout(context)
    container.setPadding(dp(context, 24), dp(context, 16), dp(context, 24), dp(context, 0))
    container.addView(
        content,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
    )
    return MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(container)
        .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .show()
}

/**
 * The unit of [spec]. Null for a spec with no unit.
 */
private fun unitLabel(context: Context, spec: ValueSpec): String? = when (spec) {
    is ValueSpec.Range -> spec.unit.suffixLabel?.let { context.getString(it) }
    is ValueSpec.Pace -> paceUnitLabel(context, spec)
    is ValueSpec.PairRange -> unitLabel(context, spec.inner)
    is ValueSpec.Time, is ValueSpec.Zone, is ValueSpec.EnumValues<*> -> null
}

private fun paceUnitLabel(context: Context, spec: ValueSpec.Pace): String = context.getString(
    if (spec.basisMeters == 100) R.string.workout_unit_suffix_per_100m else R.string.workout_unit_suffix_per_km
)

/**
 * [hint] followed by the unit of [spec] in parentheses.
 */
private fun pickerTitle(context: Context, hint: CharSequence, spec: ValueSpec): CharSequence =
    unitLabel(context, spec)?.let { "$hint ($it)" } ?: hint

/**
 * Zero-pads a wheel value to two digits.
 */
private val twoDigitFormatter = NumberPicker.Formatter { v -> v.toString().padStart(2, '0') }

/**
 * The wheels of one scalar spec. [view] holds the [pickers], [value] is the selected value.
 */
private class Wheel(val view: View, val pickers: List<NumberPicker>, val value: () -> Long) {
    fun onChanged(listener: () -> Unit) {
        pickers.forEach { it.setOnValueChangedListener { _, _, _ -> listener() } }
    }
}

private fun wheelRow(context: Context, wheels: List<NumberPicker>): View {
    val row = LinearLayout(context)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER
    wheels.forEach { picker ->
        picker.wrapSelectorWheel = false
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        params.marginStart = dp(context, 4)
        params.marginEnd = dp(context, 4)
        row.addView(picker, params)
    }
    return row
}

/**
 * A field that opens a single-choice dialog over [options]. [onChanged] runs when the user picks
 * a different option. [labelFor] gives the label of each option.
 */
fun <T : LabeledEntry> buildChoiceEditor(
    context: Context,
    options: List<T>,
    hint: CharSequence,
    initial: T?,
    onChanged: (T) -> Unit = {},
    labelFor: (T) -> CharSequence = { context.getString(it.label) },
): ChoiceRowEditor<T> {
    var current: T? = initial
    val (layout, field) = tapField(context, hint)
    fun refresh() {
        field.setText(current?.let { labelFor(it) } ?: "")
    }
    refresh()
    field.setOnClickListener {
        hideKeyboard(field)
        showChoicePicker(context, options, hint, current, labelFor) { picked ->
            val changed = picked != current
            current = picked
            refresh()
            if (changed) onChanged(picked)
        }
    }
    return ChoiceRowEditor(layout, getter = { current }) { v ->
        current = v
        refresh()
    }
}

/**
 * Shows a single-choice dialog over [options], with [initial] checked.
 */
fun <T : LabeledEntry> showChoicePicker(
    context: Context,
    options: List<T>,
    hint: CharSequence,
    initial: T?,
    labelFor: (T) -> CharSequence = { context.getString(it.label) },
    onCancel: () -> Unit = {},
    onPicked: (T) -> Unit,
) {
    val labels = options.map { labelFor(it) }.toTypedArray()
    val checkedIndex = options.indexOf(initial)
    MaterialAlertDialogBuilder(context)
        .setTitle(hint)
        .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
            dialog.dismiss()
            onPicked(options[which])
        }
        .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .show()
}

/**
 * A field bound to a text value. [setError] shows a validation message on the field.
 */
class TextValueEditor(
    val view: View,
    private val layout: TextInputLayout,
    private val getter: () -> String,
    private val setter: (String) -> Unit,
) {
    var value: String
        get() = getter()
        set(v) {
            setter(v)
        }

    fun setError(message: CharSequence?) {
        layout.error = message
    }
}

/**
 * A field that opens a text dialog with a character counter up to [maxLength]. [onChanged] runs
 * when the dialog is confirmed.
 */
fun buildTextEditor(
    context: Context,
    hint: CharSequence,
    initial: String?,
    maxLength: Int,
    onChanged: () -> Unit = {}
): TextValueEditor {
    var current: String = initial.orEmpty()
    val (layout, field) = tapField(context, hint)
    fun refresh() {
        field.setText(current)
    }
    refresh()
    field.setOnClickListener {
        showTextPicker(context, hint, current, maxLength) { picked ->
            current = picked
            refresh()
            onChanged()
        }
    }
    return TextValueEditor(layout, layout, getter = { current }) { v ->
        current = v
        refresh()
    }
}

/**
 * Removes every newline from typed or pasted text.
 */
private val noNewlineFilter = InputFilter { source, _, _, _, _, _ ->
    if (source.any { it == '\n' || it == '\r' }) source.toString().replace(Regex("[\r\n]"), "") else null
}

/**
 * Shows a dialog that edits a single-line text value of up to [maxLength] characters.
 */
fun showTextPicker(context: Context, hint: CharSequence, initial: String?, maxLength: Int, onPicked: (String) -> Unit) {
    val textLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)
    textLayout.hint = hint
    textLayout.isCounterEnabled = true
    textLayout.counterMaxLength = maxLength
    val editText = TextInputEditText(textLayout.context)
    editText.isSingleLine = true
    editText.imeOptions = EditorInfo.IME_ACTION_DONE
    editText.setText(initial)
    editText.filters = arrayOf(InputFilter.LengthFilter(maxLength), noNewlineFilter)
    editText.setSelection(editText.text?.length ?: 0)
    textLayout.addView(editText)

    val container = FrameLayout(context)
    container.setPadding(dp(context, 24), dp(context, 16), dp(context, 24), dp(context, 0))
    container.addView(
        textLayout,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    )

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(hint)
        .setView(container)
        .setPositiveButton(R.string.ok) { _, _ -> onPicked(editText.text?.toString().orEmpty()) }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    editText.requestFocus()
}

/**
 * A field bound to one scalar [spec], with [initial] as its value. [onChanged] runs when a value
 * is confirmed in the picker dialog.
 */
fun buildScalarEditor(
    context: Context,
    spec: ValueSpec,
    hint: CharSequence,
    initial: Long?,
    onChanged: () -> Unit = {}
): ScalarValueEditor {
    var current: Long? = initial?.let { clampToSpec(spec, it) }
    val (layout, field) = tapField(context, hint)
    fun refresh() {
        field.setText(current?.let { formatScalarValue(context, spec, it) } ?: "")
    }
    refresh()
    field.setOnClickListener {
        hideKeyboard(field)
        showScalarPicker(context, spec, hint, current) { picked ->
            current = picked
            refresh()
            onChanged()
        }
    }
    return ScalarValueEditor(layout, getter = { current }) { v ->
        current = v?.let { clampToSpec(spec, it) }
        refresh()
    }
}

private fun clampToSpec(spec: ValueSpec, value: Long): Long = when (spec) {
    is ValueSpec.Range -> value.coerceIn(spec.min, spec.max)
    is ValueSpec.Time -> value.coerceIn(spec.min, spec.max)
    is ValueSpec.Pace -> value.coerceIn(spec.min, spec.max)
    is ValueSpec.Zone -> value.coerceIn(spec.min.toLong(), spec.max.toLong())
    is ValueSpec.EnumValues<*> -> value
    is ValueSpec.PairRange -> value
}

/**
 * Shows the picker dialog of one scalar [spec], with [initial] selected. A labeled zone and an
 * enum show a choice list, every other spec shows wheels.
 */
fun showScalarPicker(
    context: Context,
    spec: ValueSpec,
    hint: CharSequence,
    initial: Long?,
    onCancel: () -> Unit = {},
    onPicked: (Long) -> Unit
) {
    if (spec is ValueSpec.EnumValues<*>) {
        val entries: List<LabeledEntry> = spec.values
        showChoicePicker(
            context,
            entries,
            hint,
            entries.getOrNull(initial?.toInt() ?: -1),
            onCancel = onCancel
        ) { picked ->
            onPicked(entries.indexOf(picked).toLong())
        }
        return
    }
    if (spec is ValueSpec.Zone && spec.labels.size == spec.max - spec.min + 1) {
        val choices = (spec.min..spec.max).mapIndexed { i, z -> ZoneChoice(z, spec.labels[i]) }
        val current = choices.find { it.zone == (initial?.toInt() ?: spec.default) }
        showChoicePicker(
            context,
            choices,
            hint,
            current,
            onCancel = onCancel
        ) { picked -> onPicked(picked.zone.toLong()) }
        return
    }
    val wheel = buildScalarWheel(context, spec, initial)
    showPickerDialog(context, pickerTitle(context, hint, spec), wheel.view, onCancel = onCancel) {
        onPicked(wheel.value())
    }
}

/**
 * One zone of a labeled [ValueSpec.Zone], as a choice of [showChoicePicker].
 */
private data class ZoneChoice(val zone: Int, override val label: Int) : LabeledEntry

/**
 * Shows the low and high wheels of a [ValueSpec.PairRange] side by side. OK is disabled while low
 * is above high.
 */
fun showPairPicker(
    context: Context,
    spec: ValueSpec.PairRange,
    hint: CharSequence,
    lowHint: CharSequence,
    highHint: CharSequence,
    initialLow: Long?,
    initialHigh: Long?,
    onCancel: () -> Unit = {},
    onPicked: (Long, Long) -> Unit,
) {
    val low = buildScalarWheel(context, spec.inner, initialLow ?: spec.defaultLow)
    val high = buildScalarWheel(context, spec.inner, initialHigh ?: spec.defaultHigh)

    fun labeledColumn(label: CharSequence, content: View): View {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        val labelView = TextView(context)
        labelView.text = label
        labelView.gravity = Gravity.CENTER
        column.addView(labelView)
        column.addView(content)
        return column
    }

    val row = LinearLayout(context)
    row.orientation = LinearLayout.HORIZONTAL
    row.addView(labeledColumn(lowHint, low.view), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(
        labeledColumn(highHint, high.view),
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    )

    val errorText = errorTextView(context)
    errorText.gravity = Gravity.CENTER
    errorText.setPadding(0, dp(context, 8), 0, 0)
    val content = LinearLayout(context)
    content.orientation = LinearLayout.VERTICAL
    content.addView(
        row,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    content.addView(
        errorText,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )

    val dialog = showPickerDialog(context, pickerTitle(context, hint, spec), content, onCancel = onCancel) {
        onPicked(low.value(), high.value())
    }

    fun validate() {
        val valid = low.value() <= high.value()
        setFieldError(errorText, if (valid) null else R.string.workout_error_min_above_max)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = valid
    }
    low.onChanged(::validate)
    high.onChanged(::validate)
    validate()
}

/**
 * The wheels of one [ValueSpec.Range], [ValueSpec.Time], [ValueSpec.Pace] or [ValueSpec.Zone].
 */
private fun buildScalarWheel(context: Context, spec: ValueSpec, initial: Long?): Wheel = when (spec) {
    is ValueSpec.Range -> buildRangeWheel(context, spec, initial)
    is ValueSpec.Time -> buildTimeWheels(context, spec, initial)
    is ValueSpec.Pace -> buildPaceWheels(context, spec, initial)
    is ValueSpec.Zone -> buildZoneWheel(context, spec, initial)
    is ValueSpec.EnumValues<*> -> throw IllegalArgumentException("EnumValues has no wheel, use showChoicePicker")
    is ValueSpec.PairRange -> throw IllegalArgumentException("PairRange has no single wheel, use showPairPicker")
}

private fun buildRangeWheel(context: Context, spec: ValueSpec.Range, initial: Long?): Wheel {
    val unit = spec.unit
    val step = spec.step.coerceAtLeast(1)
    val current = initial?.coerceIn(spec.min, spec.max)

    if (unit.decimals == 0 && step == 1L && spec.min >= 0) {
        return buildWholeNumberWheel(context, spec, current)
    }

    // The number of steps in one display unit
    val stepsPerUnit = (1.0 / unit.toDisplay(step)).roundToInt()
    if (unit.decimals > 0 && stepsPerUnit > 1) {
        return buildSplitRangeWheel(context, spec, current, stepsPerUnit)
    }

    return buildIndexWheel(context, spec, current, step)
}

private fun buildWholeNumberWheel(context: Context, spec: ValueSpec.Range, current: Long?): Wheel {
    val picker = NumberPicker(context)
    picker.minValue = spec.min.toInt()
    picker.maxValue = spec.max.toInt()
    picker.value = (current ?: spec.default ?: spec.min).toInt().coerceIn(spec.min.toInt(), spec.max.toInt())
    return Wheel(wheelRow(context, listOf(picker)), listOf(picker)) {
        picker.value.toLong().coerceIn(spec.min, spec.max)
    }
}

private fun buildIndexWheel(
    context: Context,
    spec: ValueSpec.Range,
    current: Long?,
    step: Long
): Wheel {
    val unit = spec.unit
    fun formatStored(v: Long): String = formatDecimal(unit.toDisplay(v), unit.decimals)
    val stepCount = ((spec.max - spec.min) / step).toInt().coerceAtLeast(0)
    val currentIndex =
        (((current ?: spec.default ?: spec.min) - spec.min).toDouble() / step).roundToInt().coerceIn(0, stepCount)
    val picker = NumberPicker(context)
    picker.minValue = 0
    picker.maxValue = stepCount
    picker.value = currentIndex
    picker.setFormatter { idx -> formatStored(spec.min + idx.toLong() * step) }
    return Wheel(wheelRow(context, listOf(picker)), listOf(picker)) {
        (spec.min + picker.value.toLong() * step).coerceIn(spec.min, spec.max)
    }
}

/**
 * A whole-unit wheel and a fraction wheel of [stepsPerUnit] increments, both in display units.
 */
private fun buildSplitRangeWheel(
    context: Context,
    spec: ValueSpec.Range,
    current: Long?,
    stepsPerUnit: Int
): Wheel {
    val unit = spec.unit
    val fractionStep = 1.0 / stepsPerUnit
    fun totalSteps(stored: Long): Int = (unit.toDisplay(stored) * stepsPerUnit).roundToInt()
    val minWhole = totalSteps(spec.min) / stepsPerUnit
    val maxWhole = totalSteps(spec.max) / stepsPerUnit
    val valueSteps = totalSteps(current ?: spec.default ?: spec.min)
    val wholePicker = NumberPicker(context).apply {
        minValue = minWhole
        maxValue = maxWhole
        this.value = (valueSteps / stepsPerUnit).coerceIn(minWhole, maxWhole)
        wrapSelectorWheel = false
    }
    val fractionPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = stepsPerUnit - 1
        this.value = valueSteps % stepsPerUnit
        wrapSelectorWheel = false
        setFormatter { idx -> formatDecimal(idx * fractionStep, unit.decimals).substringAfter('.') }
    }

    val wholeParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    wholeParams.marginStart = dp(context, 4)
    wholeParams.marginEnd = dp(context, 2)
    val fractionParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    fractionParams.marginStart = dp(context, 2)
    fractionParams.marginEnd = dp(context, 4)
    val separator = TextView(context)
    separator.text = "."
    separator.textSize = 20f
    separator.setPadding(0, 0, dp(context, 4), 0)

    val row = LinearLayout(context)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER
    row.addView(wholePicker, wholeParams)
    row.addView(separator)
    row.addView(fractionPicker, fractionParams)

    return Wheel(row, listOf(wholePicker, fractionPicker)) {
        unit.toStored(wholePicker.value + fractionPicker.value * fractionStep).coerceIn(spec.min, spec.max)
    }
}

/**
 * A bare numbered wheel, ignoring [ValueSpec.Zone.labels].
 */
private fun buildZoneWheel(context: Context, spec: ValueSpec.Zone, initial: Long?): Wheel {
    val picker = NumberPicker(context)
    picker.minValue = spec.min
    picker.maxValue = spec.max
    picker.value = (initial?.toInt() ?: spec.default ?: spec.min).coerceIn(spec.min, spec.max)
    return Wheel(wheelRow(context, listOf(picker)), listOf(picker)) { picker.value.toLong() }
}

private fun buildTimeWheels(context: Context, spec: ValueSpec.Time, initial: Long?): Wheel {
    val current = (initial ?: spec.default ?: spec.min).coerceIn(spec.min, spec.max)
    val showHours = spec.max >= 3_600_000L
    val totalSeconds = current / 1000
    val hoursPicker = if (showHours) {
        NumberPicker(context).apply {
            minValue = 0
            maxValue = (spec.max / 1000 / 3600).toInt().coerceAtLeast(1)
            value = (totalSeconds / 3600).toInt()
        }
    } else null
    val minutesPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 59
        value = ((totalSeconds % 3600) / 60).toInt()
        setFormatter(twoDigitFormatter)
    }
    val secondsPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 59
        value = (totalSeconds % 60).toInt()
        setFormatter(twoDigitFormatter)
    }
    val wheels = listOfNotNull(hoursPicker, minutesPicker, secondsPicker)
    return Wheel(wheelRow(context, wheels), wheels) {
        val h = hoursPicker?.value ?: 0
        val total = h * 3600L + minutesPicker.value * 60L + secondsPicker.value
        (total * 1000L).coerceIn(spec.min, spec.max)
    }
}

private fun buildPaceWheels(context: Context, spec: ValueSpec.Pace, initial: Long?): Wheel {
    val current = initial?.coerceIn(spec.min, spec.max) ?: spec.min
    val totalSeconds = current / 1000
    val minutePicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = (spec.max / 1000 / 60).toInt().coerceAtLeast(1)
        value = (totalSeconds / 60).toInt()
        setFormatter(twoDigitFormatter)
    }
    val secondPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 59
        value = (totalSeconds % 60).toInt()
        setFormatter(twoDigitFormatter)
    }
    val wheels = listOf(minutePicker, secondPicker)
    return Wheel(wheelRow(context, wheels), wheels) {
        ((minutePicker.value * 60L + secondPicker.value) * 1000L).coerceIn(spec.min, spec.max)
    }
}

/**
 * Formats a stored [value] of one scalar [spec] for display, with its unit.
 */
fun formatScalarValue(context: Context, spec: ValueSpec, value: Long): String = when (spec) {
    is ValueSpec.Range -> {
        val number = formatDecimal(spec.unit.toDisplay(value), spec.unit.decimals)
        val suffix = spec.unit.suffixLabel?.let { " " + context.getString(it) }.orEmpty()
        number + suffix
    }

    is ValueSpec.Time -> formatHms(value)
    is ValueSpec.Pace -> "${formatMinSec(value)} ${paceUnitLabel(context, spec)}"
    is ValueSpec.Zone -> spec.labels.getOrNull(value.toInt() - spec.min)?.let { context.getString(it) }
        ?: value.toString()

    is ValueSpec.EnumValues<*> -> spec.values.getOrNull(value.toInt())?.let { context.getString(it.label) }.orEmpty()
    is ValueSpec.PairRange -> throw IllegalArgumentException("PairRange has no single scalar value")
}

/**
 * Formats a stored [low] and [high] of a [ValueSpec.PairRange] for display, as one range.
 */
fun formatPairValue(context: Context, spec: ValueSpec.PairRange, low: Long, high: Long): String =
    formatScalarValue(context, spec.inner, low) + "–" + formatScalarValue(context, spec.inner, high)

private fun formatDecimal(value: Double, decimals: Int): String =
    if (decimals == 0)
        value.roundToLong().toString()
    else
        String.format(Locale.ROOT, "%.${decimals}f", value)

private fun formatHms(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0)
        String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    else
        String.format(Locale.ROOT, "%02d:%02d", m, s)
}

private fun formatMinSec(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}
