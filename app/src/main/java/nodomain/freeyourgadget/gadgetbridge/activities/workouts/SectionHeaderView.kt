package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R

private const val HORIZONTAL_PADDING_DP = 16
private const val VERTICAL_PADDING_DP = 16
private const val META_TOP_PADDING_DP = 3

/**
 * A section header: bold title with an optional rule above it and an optional muted line under it.
 * The one header style used across the workout tabs and the charts screens, from XML
 * (`app:sectionTitle`, `app:sectionShowDivider`) or from code via [addSectionHeader].
 *
 * The rule is drawn above the title, so a header separates itself from whatever comes before it.
 * Only the first header on a screen should turn it off.
 */
class SectionHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val divider = createDivider(context)

    private val titleView = TextView(context).apply {
        textSize = 18f
        gravity = Gravity.START
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(MaterialColors.getColor(context, R.attr.textColorPrimary, "SectionHeaderView"))
    }

    private val metaView = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.START
        setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "SectionHeaderView"))
        visibility = GONE
    }

    var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var showDivider: Boolean
        get() = divider.visibility == VISIBLE
        set(value) {
            divider.visibility = if (value) VISIBLE else GONE
        }

    /** A muted line directly under the title (e.g. a date range and a count), or null for none. */
    var meta: CharSequence?
        get() = if (metaView.visibility == VISIBLE) metaView.text else null
        set(value) {
            metaView.text = value
            metaView.visibility = if (value != null) VISIBLE else GONE
            updatePadding()
        }

    init {
        orientation = VERTICAL
        addView(divider)
        addView(titleView)
        addView(metaView)
        updatePadding()

        val a = context.obtainStyledAttributes(attrs, R.styleable.SectionHeaderView)
        try {
            a.getString(R.styleable.SectionHeaderView_sectionTitle)?.let { title = it }
            showDivider = a.getBoolean(R.styleable.SectionHeaderView_sectionShowDivider, true)
        } finally {
            a.recycle()
        }
    }

    /** The title gives its bottom padding to the meta line when there is one. */
    private fun updatePadding() {
        val horizontal = dp(HORIZONTAL_PADDING_DP)
        val vertical = dp(VERTICAL_PADDING_DP)
        val hasMeta = metaView.visibility == VISIBLE
        titleView.setPaddingRelative(horizontal, vertical, horizontal, if (hasMeta) 0 else vertical)
        metaView.setPaddingRelative(horizontal, dp(META_TOP_PADDING_DP), horizontal, vertical)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** The full-width 1dp rule a section header draws above its title. */
        @JvmStatic
        fun createDivider(context: Context): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * context.resources.displayMetrics.density).toInt()
                )
                setBackgroundColor(MaterialColors.getColor(context, R.attr.row_separator, "SectionHeaderView"))
            }
        }
    }
}
