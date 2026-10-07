package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import nodomain.freeyourgadget.gadgetbridge.R

enum class WorkoutTab(@StringRes val labelRes: Int) {
    OVERVIEW(R.string.workout_tab_overview),
    LAPS(R.string.laps),
    CHARTS(R.string.charts),
    DETAILS(R.string.workout_tab_details),
}

class WorkoutTabsPagerAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle,
    private val workoutId: Long,
) : FragmentStateAdapter(fm, lifecycle) {

    // Optional tabs start hidden; they are only added once the loaded workout is confirmed
    // to have the data they show.
    private var tabs: List<WorkoutTab> = tabsFor(hasCharts = false, hasLaps = false)

    // Tracked so the host can look up whichever tab fragment is currently visible (e.g. to
    // screenshot it), without relying on FragmentStateAdapter's internal fragment tag naming.
    private val fragmentsByItemId = mutableMapOf<Long, Fragment>()

    /**
     * Shows or hides the optional Charts and Laps tabs. Safe to call repeatedly; a no-op if unchanged.
     */
    fun setOptionalTabs(hasCharts: Boolean, hasLaps: Boolean) {
        val newTabs = tabsFor(hasCharts, hasLaps)
        if (newTabs != tabs) {
            val removedTabs = tabs - newTabs.toSet()
            tabs = newTabs
            for (removedTab in removedTabs) {
                fragmentsByItemId.remove(removedTab.ordinal.toLong())
            }
            notifyDataSetChanged()
        }
    }

    @StringRes
    fun titleResAt(position: Int): Int = tabs[position].labelRes

    override fun getItemCount(): Int = tabs.size

    // Stable per-tab-type IDs so FragmentStateAdapter can tell which fragments survived
    // a tab being added/removed, instead of misattributing fragments by position.
    override fun getItemId(position: Int): Long = tabs[position].ordinal.toLong()

    override fun containsItem(itemId: Long): Boolean = tabs.any { it.ordinal.toLong() == itemId }

    override fun createFragment(position: Int): Fragment {
        val bundle = Bundle().apply { putLong("workoutId", workoutId) }
        val fragment = when (tabs[position]) {
            WorkoutTab.OVERVIEW -> WorkoutTabOverviewFragment()
            WorkoutTab.CHARTS -> WorkoutTabChartsFragment()
            WorkoutTab.LAPS -> WorkoutTabLapsFragment()
            WorkoutTab.DETAILS -> WorkoutTabDetailsFragment()
        }
        fragment.arguments = bundle
        fragmentsByItemId[getItemId(position)] = fragment
        return fragment
    }

    /** The fragment currently shown at [position], if it has been created yet. */
    fun fragmentAt(position: Int): Fragment? = fragmentsByItemId[getItemId(position)]

    private fun tabsFor(hasCharts: Boolean, hasLaps: Boolean): List<WorkoutTab> {
        return WorkoutTab.entries.filter {
            when (it) {
                WorkoutTab.CHARTS -> hasCharts
                WorkoutTab.LAPS -> hasLaps
                else -> true
            }
        }
    }
}
