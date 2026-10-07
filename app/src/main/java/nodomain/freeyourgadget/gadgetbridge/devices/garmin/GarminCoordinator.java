/*  Copyright (C) 2024-2026 José Rebelo, Daniele Gobbetti, kuhy, Martin.JM, a0z, k4z4n0v4, ad0z,
        Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.AppManagerActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfigActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DeviceChartsProvider;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminBodyEnergySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSolarChargeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHeartRateRestingSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHrvSummarySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHrvValueSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRespiratoryRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRestingMetabolicRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepStatsSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminStressSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericTrainingLoadAcuteSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericTrainingLoadChronicSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.WorkoutLoadSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminBodyEnergySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminFitFileDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHeartRateRestingSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHrvSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHrvValueSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminIntensityMinutesSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminNapSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminRespiratoryRateSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminRestingMetabolicRateSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStageSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStatsSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSpo2SampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminStressSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHydrationSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericMetricSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.PendingFileDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.FitActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.GpxActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.RestingMetabolicRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.model.WorkoutLoadSample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BluetoothCompanyIdentifiers;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;
import nodomain.freeyourgadget.gadgetbridge.widgets.DeviceWidgetsProvider;

public abstract class GarminCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public boolean supports(@NonNull final GBDeviceCandidate candidate) {
        final SparseArray<byte[]> manufacturerSpecificData = candidate.getManufacturerSpecificData();
        final byte[] garminData = manufacturerSpecificData.get(BluetoothCompanyIdentifiers.GARMIN_INTERNATIONAL_INC);
        if (garminData != null && garminData.length >= 2) {
            final int partNumber = ((garminData[0] & 0xFF) << 8) | (garminData[1] & 0xFF);
            final DeviceType deviceType = GarminProductNumbers.getDeviceType(partNumber);
            return deviceType != null && deviceType.getCoordinatorClass() == this.getClass();
        }

        // Fallback to device name
        return super.supports(candidate);
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return false;
    }

    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        if (defaultNewSyncProtocol()) {
            // #5021 - Some new devices like Venu X1 misses a lot of files without the new sync protocol
            editor.putBoolean(DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL, true);
            // new sync protocol without MLR fails to sync large workouts
            editor.putBoolean(GarminPreferences.PREF_GARMIN_MLR, true);

            editor.apply();
        }

        if (defaultExploreSync()) {
            editor.putBoolean(GarminPreferences.PREF_GARMIN_EXPLORE_SYNC, true);
        }
    }

    public boolean defaultNewSyncProtocol() {
        return false;
    }

    public boolean defaultExploreSync() {
        return true;
    }

    @Override
    public Map<AbstractDao<?, ?>, Property> getAllDeviceDao( @NonNull final DaoSession session) {
        return new HashMap<>() {{
            put(session.getGarminActivitySampleDao(), GarminActivitySampleDao.Properties.DeviceId);
            put(session.getGarminStressSampleDao(), GarminStressSampleDao.Properties.DeviceId);
            put(session.getGarminBodyEnergySampleDao(), GarminBodyEnergySampleDao.Properties.DeviceId);
            put(session.getGarminSpo2SampleDao(), GarminSpo2SampleDao.Properties.DeviceId);
            put(session.getGarminSleepStageSampleDao(), GarminSleepStageSampleDao.Properties.DeviceId);
            put(session.getGarminEventSampleDao(), GarminEventSampleDao.Properties.DeviceId);
            put(session.getGarminFitFileDao(), GarminFitFileDao.Properties.DeviceId);
            put(session.getGarminHeartRateRestingSampleDao(), GarminHeartRateRestingSampleDao.Properties.DeviceId);
            put(session.getGarminHrvSummarySampleDao(), GarminHrvSummarySampleDao.Properties.DeviceId);
            put(session.getGarminHrvValueSampleDao(), GarminHrvValueSampleDao.Properties.DeviceId);
            put(session.getGarminIntensityMinutesSampleDao(), GarminIntensityMinutesSampleDao.Properties.DeviceId);
            put(session.getGarminNapSampleDao(), GarminNapSampleDao.Properties.DeviceId);
            put(session.getGarminRespiratoryRateSampleDao(), GarminRespiratoryRateSampleDao.Properties.DeviceId);
            put(session.getGarminRestingMetabolicRateSampleDao(), GarminRestingMetabolicRateSampleDao.Properties.DeviceId);
            put(session.getGarminSleepStatsSampleDao(), GarminSleepStatsSampleDao.Properties.DeviceId);
            put(session.getBaseActivitySummaryDao(), BaseActivitySummaryDao.Properties.DeviceId);
            put(session.getPendingFileDao(), PendingFileDao.Properties.DeviceId);
            put(session.getGenericTrainingLoadAcuteSampleDao(), GenericTrainingLoadAcuteSampleDao.Properties.DeviceId);
            put(session.getGenericTrainingLoadChronicSampleDao(), GenericTrainingLoadChronicSampleDao.Properties.DeviceId);
            put(session.getGenericMetricSampleDao(), GenericMetricSampleDao.Properties.DeviceId);
            put(session.getGenericHydrationSampleDao(), GenericHydrationSampleDao.Properties.DeviceId);
        }};
    }

    @Override
    public String getManufacturer() {
        return "Garmin";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return GarminSupport.class;
    }

    @Override
    public DeviceChartsProvider getChartsProvider() {
        return new GarminChartsProvider();
    }

    @Override
    public DeviceWidgetsProvider getWidgetsProvider() {
        return GarminWidgetsProvider.INSTANCE;
    }

    @Nullable
    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new GarminWorkoutParser(context);
    }

    @Override
    @Nullable
    public ActivityTrackProvider getActivityTrackProvider(@NonNull final GBDevice device, @NonNull final Context context) {
        // Pick the richer source when both are present:
        //  - FIT (rawDetailsPath) carries detailed sensor streams.
        //  - GPX (gpxTrack) is the polyline-only fallback that
        //    ExploreSync writes when no FIT has arrived yet.
        // If a later FIT delivery dedup-links onto an existing
        // ExploreSync row, both fields will be set; we should return
        // the FIT view rather than hide its richer data behind GPX.
        return summary -> {
            final ActivityTrackProvider provider = summary.getRawDetailsPath() != null
                    ? new FitActivityTrackProvider()
                    : new GpxActivityTrackProvider();

            return provider.getActivityTrack(summary);
        };
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, DaoSession session) {
        return new GarminActivitySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminStressSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends BodyEnergySample> getBodyEnergySampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminBodyEnergySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SolarChargeSample> getSolarChargeSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminSolarChargeSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvSummarySample> getHrvSummarySampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHrvSummarySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvValueSample> getHrvValueSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHrvValueSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends WorkoutLoadSample> getWorkoutLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new WorkoutLoadSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadAcuteSample> getTrainingAcuteLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericTrainingLoadAcuteSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadChronicSample> getTrainingChronicLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericTrainingLoadChronicSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminSpo2SampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends PaiSample> getPaiSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminPaiSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends RespiratoryRateSample> getRespiratoryRateSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminRespiratoryRateSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends RestingMetabolicRateSample> getRestingMetabolicRateProvider(final GBDevice device, final DaoSession session) {
        return new GarminRestingMetabolicRateSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SleepScoreSample> getSleepScoreProvider(final GBDevice device, final DaoSession session) {
        return new GarminSleepStatsSampleProvider(device, session);
    }

    @Override
    public HydrationSampleProvider getHydrationSampleProvider(final GBDevice device, final DaoSession session) {
        return new HydrationSampleProvider(device, session);
    }

    @Override
    public GarminHeartRateRestingSampleProvider getHeartRateRestingSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHeartRateRestingSampleProvider(device, session);
    }

    @Override
    public int[] getSupportedDeviceSpecificAuthenticationSettings() {
        return new int[]{R.xml.devicesettings_garmin_fake_oauth};
    }

    @Override
    public int[] getSupportedDeviceSpecificExperimentalSettings(final GBDevice device) {
        return new int[]{R.xml.devicesettings_garmin_experimental};
    }

    @Override
    public int[] getSupportedDebugSettings(final GBDevice device) {
        return ArrayUtils.add(
                super.getSupportedDebugSettings(device),
                R.xml.devicesettings_debug_drop_packets
        );
    }

    @Nullable
    @Override
    public WorkoutTemplateSpec getWorkoutTemplateSpec(@NonNull final GBDevice device) {
        return GarminWorkoutTemplateSpec.INSTANCE.build(this, device);
    }

    @Override
    public DeviceSettingsSpec getDeviceSettings(@NonNull final GBDevice device) {
        return GarminDeviceSettingsKt.garminDeviceSettings(device, this);
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(GBDevice device) {
        return new GarminSettingsCustomizer();
    }

    @Override
    public int getCannedRepliesSlotCount(final GBDevice device) {
        if (getPrefs(device).getBoolean(GarminPreferences.PREF_FEAT_CANNED_MESSAGES, false)) {
            return 16;
        }

        return 0;
    }

    @Override
    public int getContactsSlotCount(final GBDevice device) {
        if (getPrefs(device).getBoolean(GarminPreferences.PREF_FEAT_CONTACTS, false)) {
            return 50;
        }

        return 0;
    }

    protected static Prefs getPrefs(final GBDevice device) {
        return new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
    }

    @Override
    public boolean supportsUnicodeEmojis(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsNavigation(@NonNull final GBDevice device) {
        return supportsNotifications(device);
    }

    @Override
    public boolean supportsAppsManagement(@NonNull final GBDevice device) {
        // FIXME: experimental until better polished
        return experimentalSettingEnabled(device, "garmin_experimental_app_management");
    }

    @Override
    public boolean supportsCachedAppManagement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return AppManagerActivity.class;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppConfigurationActivity(final GBDevice device) {
        return DynamicAppConfigActivity.class;
    }

    @Override
    public boolean supportsAppListFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHydration(@NonNull final GBDevice device) {
        return getPrefs(device).getBoolean(GarminPreferences.PREF_GARMIN_HYDRATION_SUPPORTED, false);
    }

    public boolean supportsAgpsUpdates(final GBDevice device) {
        return !getPrefs(device).getString(GarminPreferences.PREF_AGPS_KNOWN_URLS, "").isEmpty();
    }

    public boolean supportsSendWaypoint(@NonNull final GBDevice device){
        return supports(device, GarminCapability.WAYPOINT_TRANSFER)
                || supports(device, GarminCapability.EXPLORE_SYNC)
                || GBApplication.getDevicePrefs(device).installUnsupportedFiles();
    }

    public boolean supports(final GBDevice device, final GarminCapability capability) {
        return getPrefs(device).getStringSet(GarminPreferences.PREF_GARMIN_CAPABILITIES, Collections.emptySet())
                .contains(capability.name());
    }

    @Override
    public boolean supportsFlashing(@NonNull GBDevice device) {
        return true;
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(Uri uri, Bundle options, Context context) {
        final GarminFitFileInstallHandler fitFileInstallHandler = new GarminFitFileInstallHandler(uri, options, context);
        if (fitFileInstallHandler.isValid())
            return fitFileInstallHandler;

        final GarminGpxRouteInstallHandler garminGpxRouteInstallHandler = new GarminGpxRouteInstallHandler(uri, context);
        if (garminGpxRouteInstallHandler.isValid())
            return garminGpxRouteInstallHandler;

        final GarminPrgFileInstallHandler prgFileInstallHandler = new GarminPrgFileInstallHandler(uri, context);
        if (prgFileInstallHandler.isValid())
            return prgFileInstallHandler;

        return null;
    }

    @Override
    public boolean supportsTrainingLoad(@NonNull final GBDevice device) {
        // Not all devices support it
        return true;
    }

}
