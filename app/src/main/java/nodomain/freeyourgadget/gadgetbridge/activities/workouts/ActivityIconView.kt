package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R

/** The icon takes 55% of the circle, so it gets (1 - 0.55) / 2 of padding on every side. */
private const val ICON_INSET_FRACTION = 0.225f

/**
 * An activity icon on a round background. Size it like any other ImageView (44dp in the workout
 * overview header, 40dp in the workout list rows) and set the icon with `setImageResource`.
 */
class ActivityIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.drawable.bg_activity_icon)
        imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.textColorPrimary))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = (minOf(w, h) * ICON_INSET_FRACTION).toInt()
        setPadding(inset, inset, inset, inset)
    }
}
