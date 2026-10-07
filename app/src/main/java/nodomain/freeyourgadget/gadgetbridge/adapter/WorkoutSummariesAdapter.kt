package nodomain.freeyourgadget.gadgetbridge.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import com.google.android.material.color.MaterialColors

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutListViewModel
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploadStatus
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addSectionDivider
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addSectionHeader
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addStatTileGrid
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class WorkoutSummariesAdapter(
    context: Context,
    private val device: GBDevice,
    private var activityKindFilter: Int,
    private var dateFromFilter: Long,
    private var dateToFilter: Long,
    private var nameContainsFilter: String?,
    private var deviceFilter: Long,
    private var itemsFilter: List<Long>?
) : AbstractActivityListingAdapter<BaseActivitySummary>(context) {
    var dashboardStats: WorkoutListViewModel.DashboardStats? = null
    var isDashboardLoading: Boolean = false

    /**
     * Upload state per summary id, for the indicator on each row. Resolved off the main thread
     * with the summaries themselves (see [WorkoutListViewModel]), because it reads the database
     * and stats the exported files; a summary missing from the map shows no indicator.
     */
    var uploadStatuses: Map<Long, WorkoutUploadStatus> = emptyMap()

    /** Ids of the summaries whose Health Connect sync failed, resolved like [uploadStatuses]. */
    var healthConnectFailures: Set<Long> = emptySet()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AbstractActivityListingViewHolder<BaseActivitySummary> {
        return when (viewType) {
            0 -> // dashboard
                DashboardViewHolder(
                    LayoutInflater.from(context).inflate(R.layout.activity_summary_dashboard_item, parent, false)
                )

            2 -> // item
                ActivityItemViewHolder(
                    LayoutInflater.from(context).inflate(R.layout.item_workout_row, parent, false)
                )

            else -> super.onCreateViewHolder(parent, viewType)
        }
    }

    override fun setActivityKindFilter(filter: Int) {
        activityKindFilter = filter
    }

    override fun setDateFromFilter(date: Long) {
        dateFromFilter = date
    }

    override fun setDateToFilter(date: Long) {
        dateToFilter = date
    }

    override fun setNameContainsFilter(name: String?) {
        nameContainsFilter = name
    }

    override fun setItemsFilter(items: List<Long>?) {
        itemsFilter = items
    }

    override fun setDeviceFilter(device: Long) {
        deviceFilter = device
    }

    fun getActivityKindFilter(): Int = activityKindFilter

    inner class ActivityItemViewHolder(itemView: View) : AbstractActivityListingViewHolder<BaseActivitySummary>(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.workout_row_icon)
        private val nameView: TextView = itemView.findViewById(R.id.workout_row_name)
        private val subtitleView: TextView = itemView.findViewById(R.id.workout_row_subtitle)
        private val photoView: ImageView = itemView.findViewById(R.id.workout_row_photo)
        private val uploadView: ImageView = itemView.findViewById(R.id.workout_row_upload)
        private val healthConnectView: ImageView = itemView.findViewById(R.id.workout_row_health_connect)
        private val gpsView: ImageView = itemView.findViewById(R.id.workout_row_gps)
        private val dateView: TextView = itemView.findViewById(R.id.workout_row_date)
        private val timeView: TextView = itemView.findViewById(R.id.workout_row_time)
        private val separatorView: View = itemView.findViewById(R.id.workout_row_separator)

        override fun fill(position: Int, summary: BaseActivitySummary, selected: Boolean) {
            val activityKind = ActivityKind.fromCode(summary.activityKind)
            val kindLabel = activityKind.getLabel(context)
            val duration = DateTimeUtils.formatDurationHoursMinutes(
                summary.endTime.time - summary.startTime.time, TimeUnit.MILLISECONDS
            )
            val hasName = !summary.name.isNullOrBlank()

            val parser = device.deviceCoordinator.getActivitySummaryParser(device, itemView.context)
            val workout = parser.parseWorkout(summary, false)

            val hasGps = when {
                workout.summary.gpxTrack != null -> true
                workout.summary.summaryData?.contains(ActivitySummaryEntries.INTERNAL_HAS_GPS) == true -> {
                    workout.data.getBoolean(ActivitySummaryEntries.INTERNAL_HAS_GPS, false)
                }

                else -> false
            }

            iconView.setImageResource(activityKind.icon)
            // Without a custom name the kind is already the title, so don't repeat it below.
            nameView.text = if (hasName) summary.name else kindLabel
            subtitleView.text = if (hasName) "$kindLabel · $duration" else duration

            val start = Calendar.getInstance().apply { time = summary.startTime }
            dateView.text = DateTimeUtils.formatDateRelative(context, summary.startTime)
            timeView.text = DateTimeUtils.formatTime(start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE))

            photoView.visibility = if (workout.summary.headerPhoto != null) View.VISIBLE else View.GONE
            gpsView.visibility = if (hasGps) View.VISIBLE else View.GONE

            val uploadStatus = uploadStatuses[summary.id]
            if (uploadStatus != null) {
                uploadView.setImageResource(uploadStatus.iconRes)
                uploadView.contentDescription = context.getString(uploadStatus.labelRes)
                uploadView.visibility = View.VISIBLE
            } else {
                uploadView.visibility = View.GONE
            }

            healthConnectView.visibility = if (summary.id in healthConnectFailures) View.VISIBLE else View.GONE

            // The last activity is followed by the end spacer, and gets no separator.
            separatorView.visibility = if (position == itemCount - 2) View.GONE else View.VISIBLE

            itemView.setBackgroundColor(if (selected) MaterialColors.getColor(itemView, R.attr.accent_color) else Color.TRANSPARENT)
        }
    }

    inner class DashboardViewHolder(itemView: View) : AbstractActivityListingViewHolder<BaseActivitySummary>(itemView) {
        private val loadingSpinner: ProgressBar = itemView.findViewById(R.id.summary_dashboard_layout_loading)
        private val contentLayout: LinearLayout = itemView.findViewById(R.id.summary_dashboard_layout_content)

        override fun fill(position: Int, summary: BaseActivitySummary, selected: Boolean) {
            loadingSpinner.visibility = if (isDashboardLoading) View.VISIBLE else View.GONE
            setPlaceholderEffect(isDashboardLoading)

            val stats = dashboardStats ?: if (isDashboardLoading) placeholderStats() else null
            if (stats != null) {
                bindStats(stats)
            }
        }

        private fun setPlaceholderEffect(loading: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = 8 * context.resources.displayMetrics.density
                contentLayout.setRenderEffect(
                    if (loading) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) else null
                )
            } else {
                contentLayout.alpha = if (loading) 0.35f else 1f
            }
        }

        private fun placeholderStats(): WorkoutListViewModel.DashboardStats {
            val now = System.currentTimeMillis()
            return WorkoutListViewModel.DashboardStats(
                durationSum = TimeUnit.HOURS.toMillis(6),
                caloriesBurntSum = 1234.0,
                distanceSum = 12300.0,
                activeSecondsSum = TimeUnit.MINUTES.toSeconds(330),
                firstItemDate = now,
                lastItemDate = now,
                activityIcon = 0
            )
        }

        private fun bindStats(stats: WorkoutListViewModel.DashboardStats) {
            val activitiesCount = (itemCount - 2).coerceAtLeast(0) // remove dashboard and end spacer

            contentLayout.removeAllViews()

            val title = if (activityKindFilter != 0) {
                ActivityKind.fromCode(activityKindFilter).getLabel(context)
            } else {
                context.getString(R.string.activity_summaries_all_activities)
            }

            // start and end are inverted when filter not applied, because items are sorted the other way
            val timeStart = if (dateFromFilter != 0L) {
                DateTimeUtils.formatDate(Date(dateFromFilter))
            } else {
                DateTimeUtils.formatDate(Date(stats.lastItemDate))
            }
            val timeEnd = if (dateToFilter != 0L) {
                DateTimeUtils.formatDate(Date(dateToFilter))
            } else {
                DateTimeUtils.formatDate(Date(stats.firstItemDate))
            }
            val meta = context.getString(
                R.string.workout_list_meta,
                timeStart,
                timeEnd,
                context.resources.getQuantityString(R.plurals.workout_list_activity_count, activitiesCount, activitiesCount)
            )
            addSectionHeader(contentLayout, context, title, showDivider = false, meta = meta)

            val totals = mutableListOf(
                StatTileData(
                    FormatUtils.getFormattedDistanceLabel(stats.distanceSum),
                    context.getString(R.string.distance),
                    icon = R.drawable.ic_distance
                ),
                StatTileData(
                    String.format("%s %s", stats.caloriesBurntSum.toLong(), context.getString(R.string.calories_unit)),
                    context.getString(R.string.caloriesBurnt),
                    icon = R.drawable.ic_calories
                ),
                StatTileData(
                    DateTimeUtils.formatDurationHoursMinutes(stats.activeSecondsSum, TimeUnit.SECONDS),
                    context.getString(R.string.activity_list_summary_active_time),
                    icon = R.drawable.ic_timer
                ),
            )
            // A zero total duration means the summaries carry no usable start/end, so the tile
            // would only show a misleading "0"; the grid pads the odd tile out to half width.
            if (stats.durationSum > 0) {
                totals.add(
                    StatTileData(
                        DateTimeUtils.formatDurationHoursMinutes(stats.durationSum, TimeUnit.MILLISECONDS),
                        context.getString(R.string.activity_detail_duration_label),
                        icon = R.drawable.ic_hourglass_empty
                    )
                )
            }
            addStatTileGrid(contentLayout, context, totals, horizontalMarginDp = 16)

            // Gap, then a rule, between the totals and the list of activities.
            contentLayout.addView(Space(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (16 * context.resources.displayMetrics.density).toInt()
                )
            })
            addSectionDivider(contentLayout, context)
        }
    }
}
