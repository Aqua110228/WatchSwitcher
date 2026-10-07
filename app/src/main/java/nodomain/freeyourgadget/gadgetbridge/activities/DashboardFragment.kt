/*  Copyright (C) 2023-2026 Arjan Schrijver, José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardAdapter
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardCalendarActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardViewModel
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.BarShade
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Hosts the widget grid: date header, calendar/settings menu, and the [RecyclerView] the
 * [DashboardAdapter] renders configured widgets into, driven by [DashboardViewModel].
 */
class DashboardFragment : Fragment(), MenuProvider, HeaderShadePage {
    private val day: Calendar = GregorianCalendar.getInstance()
    private lateinit var textViewDate: TextView
    private lateinit var arrowRight: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DashboardAdapter
    private lateinit var viewModel: DashboardViewModel

    private var isConfigChanged = false

    private val calendarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val timeMillis = result.data!!.getLongExtra(DashboardCalendarActivity.EXTRA_TIMESTAMP, 0)
            if (timeMillis != 0L) {
                day.timeInMillis = timeMillis
                fullRefresh()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBApplication.ACTION_NEW_DATA -> {
                    val dev: GBDevice? = intent.getDevice()
                    if (dev != null && isDeviceInScope(dev)) {
                        refresh()
                    }
                }

                ACTION_CONFIG_CHANGE -> isConfigChanged = true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        textViewDate = view.findViewById(R.id.dashboard_date)
        recyclerView = view.findViewById(R.id.dashboard_recyclerview)
        arrowRight = view.findViewById(R.id.arrow_right)

        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        adapter = DashboardAdapter()

        val metrics = resources.displayMetrics
        val spanCount = if (metrics.widthPixels / metrics.density >= 600) 4 else 2
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = adapter.columnsAt(position).coerceAtMost(spanCount)
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val arrowLeft = view.findViewById<TextView>(R.id.arrow_left)
        arrowLeft.setOnClickListener {
            day.add(Calendar.DAY_OF_MONTH, -1)
            refresh()
        }
        arrowRight.setOnClickListener {
            val today = GregorianCalendar.getInstance()
            if (!DateTimeUtils.isSameDay(today, day)) {
                day.add(Calendar.DAY_OF_MONTH, 1)
                refresh()
            }
        }

        savedInstanceState?.let {
            if (it.containsKey(KEY_DAY)) {
                day.timeInMillis = it.getLong(KEY_DAY)
            }
        }

        observeViewModel()
        refresh()

        val filter = IntentFilter()
        filter.addAction(GBDevice.ACTION_DEVICE_CHANGED)
        filter.addAction(GBApplication.ACTION_NEW_DATA)
        filter.addAction(ACTION_CONFIG_CHANGE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filter)

        return view
    }

    override fun onResume() {
        super.onResume()
        if (isConfigChanged) {
            isConfigChanged = false
            fullRefresh()
        }
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DAY, day.timeInMillis)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.dashboard_show_calendar -> {
                val intent = Intent(requireActivity(), DashboardCalendarActivity::class.java)
                intent.putExtra(DashboardCalendarActivity.EXTRA_TIMESTAMP, day.timeInMillis)
                calendarLauncher.launch(intent)
                true
            }

            R.id.dashboard_settings -> {
                startActivity(Intent(requireActivity(), DashboardPreferencesActivity::class.java))
                true
            }

            else -> false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.instances.collect { instances ->
                adapter.submitInstances(instances)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { states ->
                adapter.submitStates(states)
            }
        }
    }

    private fun fullRefresh() {
        refresh()
    }

    private fun refresh() {
        day.set(Calendar.HOUR_OF_DAY, 23)
        day.set(Calendar.MINUTE, 59)
        day.set(Calendar.SECOND, 59)

        val prefs = GBApplication.getPrefs()
        val showAllDevices = prefs.getBoolean("dashboard_devices_all", true)
        val deviceList = prefs.getStringSet("dashboard_devices_multiselect", HashSet())
        val timestamp = (day.timeInMillis / 1000L).toInt()

        adapter.cardsEnabled = prefs.getBoolean("dashboard_cards_enabled", true)
        adapter.dashboardShowAllDevices = showAllDevices
        adapter.dashboardDeviceList = deviceList
        adapter.timestamp = timestamp

        val today = GregorianCalendar.getInstance()
        if (DateTimeUtils.isSameDay(today, day)) {
            textViewDate.text = requireContext().getString(R.string.activity_summary_today)
            arrowRight.alpha = 0.5f
        } else {
            textViewDate.text = DateTimeUtils.formatDate(day.time, DateUtils.FORMAT_SHOW_WEEKDAY)
            arrowRight.alpha = 1f
        }

        viewModel.refresh(day, showAllDevices, deviceList)
    }

    override fun getHeaderShade(): View? {
        val shade = view?.findViewById<View>(R.id.dashboard_date_row_shade) ?: return null
        return if (BarShade.continuesToolbar(shade.context, R.attr.datestep_row_bg)) shade else null
    }

    private fun isDeviceInScope(device: GBDevice): Boolean {
        val prefs = GBApplication.getPrefs()
        val showAllDevices = prefs.getBoolean("dashboard_devices_all", true)
        val deviceList = prefs.getStringSet("dashboard_devices_multiselect", HashSet())
        return showAllDevices || deviceList.contains(device.address)
    }

    companion object {
        const val ACTION_CONFIG_CHANGE =
            "nodomain.freeyourgadget.gadgetbridge.activities.dashboardfragment.action.config_change"
        private const val KEY_DAY = "dashboard_day"
    }
}
