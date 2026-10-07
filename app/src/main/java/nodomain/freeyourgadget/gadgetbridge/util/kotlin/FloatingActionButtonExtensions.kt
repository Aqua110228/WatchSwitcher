package nodomain.freeyourgadget.gadgetbridge.util.kotlin

import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Colors the button with the user's accent color, and its icon with the color that goes with it
 * (white, or black on a white accent). Both follow the accent presets and Dynamic Color.
 */
fun FloatingActionButton.applyAccentColors() {
    backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.tab_pill))
    imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.tab_pill_on))
}
