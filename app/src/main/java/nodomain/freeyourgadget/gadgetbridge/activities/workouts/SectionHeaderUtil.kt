package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.widget.LinearLayout

/**
 * Adds a [SectionHeaderView], styled consistently across the workout tabs and the charts screens.
 * Pass `showDivider = false` only for the first header on a screen. [meta], when set, is a muted
 * line directly under the title (e.g. a date range and a count).
 */
fun addSectionHeader(container: LinearLayout, context: Context, title: String, showDivider: Boolean, meta: String? = null) {
    container.addView(SectionHeaderView(context).also {
        it.title = title
        it.showDivider = showDivider
        it.meta = meta
    })
}

/** Adds the same full-width rule a section header draws above its title, without a header. */
fun addSectionDivider(container: LinearLayout, context: Context) {
    container.addView(SectionHeaderView.createDivider(context))
}
