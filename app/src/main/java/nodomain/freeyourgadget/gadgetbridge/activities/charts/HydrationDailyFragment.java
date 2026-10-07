package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBAccess;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.HydrationContainer;
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class HydrationDailyFragment extends HydrationFragment<HydrationDailyFragment.HydrationData> {
    private static final int INCREMENT_ML = 250;

    private ImageView hydrationGauge;
    private TextView dateView;
    private TextView hydrationTotal;
    private TextView hydrationGoal;
    private Button addIncrementButton;
    private Button addButton;
    private Button removeIncrementButton;
    private final Button[] containerButtons = new Button[HydrationContainer.COUNT];

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_hydration, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        hydrationGauge = rootView.findViewById(R.id.hydration_gauge);
        dateView = rootView.findViewById(R.id.date_view);
        hydrationTotal = rootView.findViewById(R.id.hydration_total);
        hydrationGoal = rootView.findViewById(R.id.hydration_goal);
        addIncrementButton = rootView.findViewById(R.id.hydration_add_increment);
        addButton = rootView.findViewById(R.id.hydration_add);
        removeIncrementButton = rootView.findViewById(R.id.hydration_remove_increment);
        containerButtons[0] = rootView.findViewById(R.id.hydration_add_container_1);
        containerButtons[1] = rootView.findViewById(R.id.hydration_add_container_2);
        containerButtons[2] = rootView.findViewById(R.id.hydration_add_container_3);

        refresh();

        return rootView;
    }

    @Override
    protected HydrationData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final LocalDate date = toLocalDate(chartsHost.getEndDate());
        final HydrationSampleProvider provider = device.getDeviceCoordinator().getHydrationSampleProvider(device, db.getDaoSession());
        final double totalMl = provider != null ? provider.getDayTotal(HydrationSampleProvider.toDay(date)) : 0;
        final int goalMl = new ActivityUser().getHydrationGoalMl();
        final Prefs prefs = GBApplication.getDevicePrefs(device);
        final double[] containerVolumesMl = new double[HydrationContainer.COUNT];
        final HydrationUnit[] containerUnits = new HydrationUnit[HydrationContainer.COUNT];
        for (int i = 0; i < HydrationContainer.COUNT; i++) {
            containerVolumesMl[i] = HydrationContainer.getVolumeMl(prefs, i + 1);
            containerUnits[i] = HydrationContainer.getUnit(prefs, i + 1);
        }
        return new HydrationData(date, totalMl, goalMl, HydrationUnit.forDevice(device), containerVolumesMl, containerUnits);
    }

    @Override
    protected void updateChartsnUIThread(final HydrationData data) {
        final Context context = requireContext();
        final HydrationUnit unit = data.unit;

        dateView.setText(data.date.format(DateTimeFormatter.ofPattern("E, MMM dd", Locale.getDefault())));
        hydrationTotal.setText(unit.format(context, data.totalMl));
        hydrationGoal.setText(unit.format(context, data.goalMl));

        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                300,
                GBApplication.getContext().getResources().getDisplayMetrics()
        );
        hydrationGauge.setImageBitmap(GaugeDrawer.drawCircleGauge(
                width,
                width / 15,
                ContextCompat.getColor(context, R.color.hydration_color),
                (int) Math.round(Math.max(data.totalMl, 0)),
                data.goalMl,
                context
        ));

        final boolean editable = !data.date.isAfter(LocalDate.now());
        final int day = HydrationSampleProvider.toDay(data.date);
        addIncrementButton.setText(getString(R.string.hydration_add_increment, unit.format(context, INCREMENT_ML)));
        addIncrementButton.setEnabled(editable);
        addIncrementButton.setOnClickListener(v -> addEntry(day, INCREMENT_ML));
        addButton.setEnabled(editable);
        addButton.setOnClickListener(v -> showAddDialog(day, unit));
        removeIncrementButton.setText(unit.format(context, -INCREMENT_ML));
        removeIncrementButton.setEnabled(editable);
        removeIncrementButton.setOnClickListener(v -> addEntry(day, -INCREMENT_ML));
        for (int i = 0; i < HydrationContainer.COUNT; i++) {
            final double volumeMl = data.containerVolumesMl[i];
            containerButtons[i].setText(getString(R.string.hydration_add_increment, data.containerUnits[i].format(context, volumeMl)));
            containerButtons[i].setEnabled(editable);
            containerButtons[i].setOnClickListener(v -> addEntry(day, volumeMl));
        }
    }

    @Override
    protected void renderCharts() {
    }

    private void showAddDialog(final int day, final HydrationUnit unit) {
        final Context context = requireContext();
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        final int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
        final FrameLayout inputContainer = new FrameLayout(context);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(margin);
        params.setMarginEnd(margin);
        inputContainer.addView(input, params);

        new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.hydration_add_title, getString(unit.getSymbol())))
                .setView(inputContainer)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final double value;
                    try {
                        value = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                    } catch (final NumberFormatException e) {
                        GB.toast(context, R.string.hydration_add_invalid_value, Toast.LENGTH_SHORT, GB.WARN);
                        return;
                    }
                    if (value != 0) {
                        addEntry(day, value * unit.getMl());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addEntry(final int day, final double volumeMl) {
        new AddEntryTask(requireContext(), getChartsHost().getDevice(), day, volumeMl)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @SuppressLint("StaticFieldLeak")
    private static class AddEntryTask extends DBAccess {
        private final GBDevice device;
        private final int day;
        private final double volumeMl;

        AddEntryTask(final Context context, final GBDevice device, final int day, final double volumeMl) {
            super("Adding hydration entry", context, true);
            this.device = device;
            this.day = day;
            this.volumeMl = volumeMl;
        }

        @Override
        protected void doInBackground(final DBHandler db) {
            final HydrationSampleProvider provider = device.getDeviceCoordinator().getHydrationSampleProvider(device, db.getDaoSession());
            if (provider != null) {
                provider.addEntry(day, System.currentTimeMillis(), volumeMl);
            }
        }

        @Override
        protected void onPostExecute(final Object o) {
            super.onPostExecute(o);
            if (getTaskError() == null) {
                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(ChartsHost.REFRESH));
                if (day == HydrationSampleProvider.toDay(LocalDate.now()) && device.isInitialized()) {
                    GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_HYDRATION_SEND_TODAY);
                }
            }
        }
    }

    protected static class HydrationData extends ChartsData {
        private final LocalDate date;
        private final double totalMl;
        private final int goalMl;
        private final HydrationUnit unit;
        private final double[] containerVolumesMl;
        private final HydrationUnit[] containerUnits;

        protected HydrationData(final LocalDate date,
                                final double totalMl,
                                final int goalMl,
                                final HydrationUnit unit,
                                final double[] containerVolumesMl,
                                final HydrationUnit[] containerUnits) {
            this.date = date;
            this.totalMl = totalMl;
            this.goalMl = goalMl;
            this.unit = unit;
            this.containerVolumesMl = containerVolumesMl;
            this.containerUnits = containerUnits;
        }
    }
}
