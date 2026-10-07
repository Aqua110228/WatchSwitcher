package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.ElevationOverlayProvider
import nodomain.freeyourgadget.gadgetbridge.R

private const val COLUMNS = 2
private const val CARD_CORNER_RADIUS_DP = 12
private const val TILE_GAP_DP = 12
private const val TILE_PADDING_HORIZONTAL_DP = 12
private const val TILE_PADDING_VERTICAL_DP = 16
private const val SUBTEXT_TOP_MARGIN_DP = 2
private const val SUBTEXT_DRAWABLE_PADDING_DP = 4
private const val ICON_TILE_ICON_SIZE_DP = 22
private const val ICON_TILE_GAP_DP = 12
private const val ICON_TILE_PADDING_HORIZONTAL_DP = 12
private const val ICON_TILE_PADDING_VERTICAL_DP = 14
private const val ICON_TILE_LABEL_TOP_MARGIN_DP = 3

/**
 * [subtext] is an optional line rendered between [value] and [label] (e.g. a trend indicator),
 * shown with a leading [subtextIcon] tinted and colored with [subtextColor]. Omitted entirely
 * (no reserved space) when [subtext] is null. [borderColor], when set, replaces the tile's
 * neutral border with that color (e.g. to identify one series/zone among several tiles) in place
 * of a separate colored swatch. [icon], when set, switches to the horizontal variant: the icon on
 * the left, then the value and label start-aligned next to it, instead of the centered layout.
 */
data class StatTileData @JvmOverloads constructor(
    val value: String,
    val label: String,
    val valueColor: Int? = null,
    val subtext: String? = null,
    @DrawableRes val subtextIcon: Int? = null,
    val subtextColor: Int? = null,
    val borderColor: Int? = null,
    @DrawableRes val icon: Int? = null,
)

/**
 * Adds a 2-column grid of [StatTile][buildStatTile] cards to [container], one row per two
 * [stats]. A lone tile on the last row gets a same-width invisible filler next to it rather than
 * stretching to full width. [horizontalMarginDp] adds extra left/right margin on top of whatever
 * inset [container] already applies, for screens where the grid needs to line up with a header
 * that itself sits further in than [container]'s own padding.
 */
fun addStatTileGrid(container: LinearLayout, context: Context, stats: List<StatTileData>, horizontalMarginDp: Int = 0) {
    if (stats.isEmpty()) {
        return
    }

    val grid = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(context, horizontalMarginDp)
            marginEnd = dpToPx(context, horizontalMarginDp)
        }
    }

    stats.chunked(COLUMNS).forEachIndexed { rowIndex, rowStats ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) {
                    topMargin = dpToPx(context, TILE_GAP_DP)
                }
            }
        }

        rowStats.forEachIndexed { columnIndex, stat ->
            // Deliberately WRAP_CONTENT, not MATCH_PARENT: this row sits inside a ScrollView, so
            // LinearLayout's usual "stretch to tallest sibling" trick doesn't reliably apply here.
            // Forcing a shared height clipped a tile whenever its row sibling was shorter (e.g. a
            // value that wrapped to 2 lines got its second line cut off) - letting each card size
            // to its own content means row siblings can differ slightly in height, but nothing is
            // ever cut off.
            val cardParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (columnIndex > 0) {
                    marginStart = dpToPx(context, TILE_GAP_DP)
                }
            }
            row.addView(buildStatTile(context, stat), cardParams)
        }
        repeat(COLUMNS - rowStats.size) {
            val fillerParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                marginStart = dpToPx(context, TILE_GAP_DP)
            }
            row.addView(View(context), fillerParams)
        }

        grid.addView(row)
    }

    container.addView(grid)
}

private fun buildStatTile(context: Context, stat: StatTileData): MaterialCardView {
    return MaterialCardView(context).apply {
        radius = dpToPx(context, CARD_CORNER_RADIUS_DP).toFloat()
        cardElevation = 0f
        // A colored border identifies one series/zone among several tiles, so it stays thicker
        // to remain visible; the neutral border is a thin outline.
        strokeWidth = dpToPx(context, if (stat.borderColor != null) 2 else 1)
        setStrokeColor(stat.borderColor ?: MaterialColors.getColor(context, R.attr.stat_tile_border, "StatTile"))
        setCardBackgroundColor(
            ElevationOverlayProvider(context).compositeOverlayIfNeeded(
                MaterialColors.getColor(context, R.attr.stat_tile_bg, "StatTile"),
                3 * context.resources.displayMetrics.density
            )
        )

        val content = if (stat.icon != null) {
            buildIconTileContent(context, stat, stat.icon)
        } else {
            buildCenteredTileContent(context, stat)
        }

        addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        )
    }
}

private fun buildIconTileContent(context: Context, stat: StatTileData, @DrawableRes icon: Int): LinearLayout {
    val mutedColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "StatTile")
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(
            dpToPx(context, ICON_TILE_PADDING_HORIZONTAL_DP),
            dpToPx(context, ICON_TILE_PADDING_VERTICAL_DP),
            dpToPx(context, ICON_TILE_PADDING_HORIZONTAL_DP),
            dpToPx(context, ICON_TILE_PADDING_VERTICAL_DP)
        )

        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(mutedColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(context, ICON_TILE_ICON_SIZE_DP),
                dpToPx(context, ICON_TILE_ICON_SIZE_DP)
            )
        })

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(context, ICON_TILE_GAP_DP)
            }

            addView(TextView(context).apply {
                text = stat.value
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(stat.valueColor ?: MaterialColors.getColor(context, R.attr.textColorPrimary, "StatTile"))
            })
            addView(TextView(context).apply {
                text = stat.label
                textSize = 12f
                maxLines = 2
                setTextColor(mutedColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(context, ICON_TILE_LABEL_TOP_MARGIN_DP)
                }
            })
        })
    }
}

private fun buildCenteredTileContent(context: Context, stat: StatTileData): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPaddingRelative(
            dpToPx(context, TILE_PADDING_HORIZONTAL_DP),
            dpToPx(context, TILE_PADDING_VERTICAL_DP),
            dpToPx(context, TILE_PADDING_HORIZONTAL_DP),
            dpToPx(context, TILE_PADDING_VERTICAL_DP)
        )

        addView(TextView(context).apply {
            text = stat.value
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(stat.valueColor ?: MaterialColors.getColor(context, R.attr.textColorPrimary, "StatTile"))
            // MATCH_PARENT width (content's vertical LinearLayout would default to this
            // anyway) so the gravity above actually centers the text - a WRAP_CONTENT-width
            // TextView here would just get centered as a box, not the text within it.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        if (stat.subtext != null) {
            addView(TextView(context).apply {
                text = stat.subtext
                textSize = 12f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                compoundDrawablePadding = dpToPx(context, SUBTEXT_DRAWABLE_PADDING_DP)
                val color = stat.subtextColor ?: MaterialColors.getColor(context, R.attr.textColorPrimary, "StatTile")
                setTextColor(color)
                if (stat.subtextIcon != null) {
                    TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        this,
                        ResourcesCompat.getDrawable(resources, stat.subtextIcon, context.theme),
                        null,
                        null,
                        null
                    )
                    TextViewCompat.setCompoundDrawableTintList(this, android.content.res.ColorStateList.valueOf(color))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dpToPx(context, SUBTEXT_TOP_MARGIN_DP)
                }
            })
        }
        addView(TextView(context).apply {
            text = stat.label
            textSize = 12f
            maxLines = 2
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "StatTile"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 4)
            }
        })
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
