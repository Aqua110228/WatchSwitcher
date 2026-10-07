/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Damien
    Gaignon, Daniel Dakhno, Daniele Gobbetti, Dmitry Markin, José Rebelo,
    Lem Dulfo, Martin Braun, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.GBAlarmListAdapter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.Alarm;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.util.AlarmUtils;


public class ConfigureAlarms extends AbstractGBActivity {
    private static final int REQ_CONFIGURE_ALARM = 1;
    private static final String PREF_SHOW_UNUSED_ALARMS = "alarms_show_unused";

    private GBAlarmListAdapter mGBAlarmListAdapter;
    private FloatingActionButton fab;
    private boolean avoidSendAlarmsToDevice;
    private GBDevice gbDevice;
    private ArrayList<Alarm> fullAlarmList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_alarms);

        IntentFilter filterLocal = new IntentFilter();
        filterLocal.addAction(DeviceService.ACTION_SAVE_ALARMS);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filterLocal);

        gbDevice = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);

        mGBAlarmListAdapter = new GBAlarmListAdapter(this);

        RecyclerView alarmsRecyclerView = findViewById(R.id.alarm_list);
        alarmsRecyclerView.setHasFixedSize(true);
        alarmsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        alarmsRecyclerView.setAdapter(mGBAlarmListAdapter);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> addAlarm());

        updateAlarmsFromDB();
    }

    @Override
    protected void onPause() {
        if (!avoidSendAlarmsToDevice && gbDevice.isInitialized()) {
            sendAlarmsToDevice();
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONFIGURE_ALARM) {
            avoidSendAlarmsToDevice = false;
            updateAlarmsFromDB();
        }
    }

    /**
     * Reads the available alarms from the database and updates the view afterwards.
     */
    public void updateAlarmsFromDB() {
        List<Alarm> alarms = DBHelper.getAlarms(getGbDevice());
        if (alarms.isEmpty()) {
            alarms = AlarmUtils.readAlarmsFromPrefs(getGbDevice());
            storeMigratedAlarms(alarms);
        }
        DBHelper.fillMissingAlarms(gbDevice, alarms);
        fullAlarmList = new ArrayList<>(alarms);

        final boolean showUnused = showUnusedAlarms();
        List<Alarm> visibleAlarms;
        if (showUnused) {
            // Old behavior - show all unused alarms in their slot's position.
            visibleAlarms = fullAlarmList;
        } else {
            // New default behavior - only used alarms are shown, sorted by time, otherwise
            // newly-added alarms would pop up in whatever slot is free.
            visibleAlarms = new ArrayList<>();
            for (Alarm alarm : alarms) {
                if (!alarm.getUnused()) {
                    visibleAlarms.add(alarm);
                }
            }
            visibleAlarms.sort(Comparator.comparingInt(Alarm::getHour).thenComparingInt(Alarm::getMinute));
        }
        fab.setVisibility(showUnused ? View.GONE : View.VISIBLE);

        mGBAlarmListAdapter.setAlarmList(visibleAlarms);
        mGBAlarmListAdapter.notifyDataSetChanged();
    }

    /**
     * Opens the editor on the first free (unused) alarm slot, or informs the user that
     * the device does not have any free slots left.
     */
    private void addAlarm() {
        for (Alarm alarm : fullAlarmList) {
            if (alarm.getUnused()) {
                configureAlarm(alarm);
                return;
            }
        }
        Toast.makeText(this, R.string.alarm_max_slots_reached, Toast.LENGTH_SHORT).show();
    }

    private void storeMigratedAlarms(List<Alarm> alarms) {
        for (Alarm alarm : alarms) {
            DBHelper.store(alarm);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.configure_alarms, menu);
        menu.findItem(R.id.alarms_action_show_unused).setChecked(showUnusedAlarms());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // back button
            finish();
            return true;
        }
        if (itemId == R.id.alarms_action_show_unused) {
            final boolean newValue = !item.isChecked();
            GBApplication.getPrefs().getPreferences().edit()
                    .putBoolean(PREF_SHOW_UNUSED_ALARMS, newValue)
                    .apply();
            item.setChecked(newValue);
            updateAlarmsFromDB();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean showUnusedAlarms() {
        return GBApplication.getPrefs().getBoolean(PREF_SHOW_UNUSED_ALARMS, false);
    }

    public void configureAlarm(Alarm alarm) {
        avoidSendAlarmsToDevice = true;
        Intent startIntent = new Intent(getApplicationContext(), AlarmDetails.class);
        startIntent.putExtra(Alarm.EXTRA_ALARM, alarm);
        startIntent.putExtra(GBDevice.EXTRA_DEVICE, getGbDevice());
        startActivityForResult(startIntent, REQ_CONFIGURE_ALARM);
    }

    private GBDevice getGbDevice() {
        return gbDevice;
    }

    private void sendAlarmsToDevice() {
        // We always send the full alarm list to the device support classes, including all unused alarms,
        // since a lot of old code assumes that all alarm slots are always present. The old UI used to
        // enforce that all slots were always visible, and it was only possible to disable them.
        GBApplication.deviceService(gbDevice).onSetAlarms(fullAlarmList);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case DeviceService.ACTION_SAVE_ALARMS: {
                    updateAlarmsFromDB();
                    break;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onDestroy();
    }

}
