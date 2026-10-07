/*  Copyright (C) 2024-2026 kuhy, José Rebelo, Thomas Kuehne

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

import static nodomain.freeyourgadget.gadgetbridge.util.GB.toast;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitLocalMessageBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.FitDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitDeviceSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitUserProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

public class GarminSettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    private static final Logger LOG = LoggerFactory.getLogger(GarminSettingsCustomizer.class);

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
        final PreferenceCategory blacklistedDomains = handler.findPreference("pref_category_internet_firewall_blacklisted_domains");
        if (blacklistedDomains != null) {
            addBlockedDomain(handler, blacklistedDomains, "pref_blacklisted_url_garmin_com", "garmin.com");
            addBlockedDomain(handler, blacklistedDomains, "pref_blacklisted_url_dciwx_com", "dciwx.com");
            addBlockedDomain(handler, blacklistedDomains, "pref_blacklisted_url_garmin_cn", "garmin.cn");
        }

        final Preference prefSleepSend = handler.findPreference("garmin_experimental_sleep_send");
        if (prefSleepSend != null) {
            prefSleepSend.setOnPreferenceClickListener(dummy -> sendSleep(handler));
        }

        final Preference prefConnection = handler.findPreference("garmin_experimental_connection_send");
        if (prefConnection != null) {
            prefConnection.setOnPreferenceClickListener(dummy -> sendConnection(handler));
        }
    }

    private boolean sendConnection(DeviceSpecificSettingsHandler handler) {
        final GBDevice device = handler.getDevice();
        if (!device.isInitialized()) {
            LOG.warn("ConnectionTest device: {}", device.getState());
            toast(handler.getContext(), R.string.device_not_connected, Toast.LENGTH_LONG, GB.ERROR);
            return false;
        }

        final FitLocalMessageBuilder messages = new FitLocalMessageBuilder();
        final long now = System.currentTimeMillis() / 1000L;

        final FitFileId.Builder fileId = new FitFileId.Builder();
        fileId.setType(FileType.FILETYPE.SETTINGS);
        fileId.setManufacturer(FitDevice.M1_P65534.manufacturer);
        fileId.setProduct(FitDevice.M1_P65534.product);
        fileId.setSerialNumber(1L);
        fileId.setTimeCreated(now);
        fileId.setNumber(0);
        messages.addRecordData(fileId.build(messages.getNextAvailableLocalMessageType()));

        final DevicePrefs prefs = GBApplication.getDevicePrefs(device);
        final int wifi = prefs.getInt("garmin_experimental_connection_Wifi", -2);
        final int wifiAutoUpload = prefs.getInt("garmin_experimental_connection_WifiAutoUpload", -2);

        final FitDeviceSettings.Builder settings = new FitDeviceSettings.Builder();
        if (wifi != -2) {
            settings.setWifiEnabled(wifi == -1 ? null : wifi);
        }
        if (wifiAutoUpload != -2) {
            settings.setWifiAutoUploadEnabled(wifiAutoUpload == -1 ? null : wifiAutoUpload);
        }
        messages.addRecordData(settings.build(messages.getNextAvailableLocalMessageType()));

        final FitFile fitFile = new FitFile(messages.getRecordDataList());
        final byte[] fitBytes = fitFile.getOutgoingMessage();

        final Uri uri = Uri.parse("fake://SendConnection");
        final Bundle options = new Bundle();
        options.putByteArray(AbstractDeviceSupport.BUNDLE_EXTRA_INSTALL_BYTES, fitBytes);
        options.putString(AbstractDeviceSupport.BUNDLE_EXTRA_INSTALL_TASK_NAME, "configure SendConnection");

        final DeviceService deviceService = GBApplication.deviceService(device);
        LOG.info("send SendConnection to device: Wifi={} WifiAutoUpload={}",
            wifi, wifiAutoUpload);
        deviceService.onInstallApp(uri, options);
        return true;
    }

    private void addBlockedDomain(final DeviceSpecificSettingsHandler handler,
                                  final PreferenceCategory category,
                                  final String key,
                                  final String domain) {
        final Preference preference = new Preference(handler.getContext());
        preference.setKey(key);
        preference.setPersistent(false);
        preference.setSelectable(false);
        preference.setIcon(R.drawable.ic_block);
        preference.setSummary(domain);
        category.addPreference(preference);
    }

    private boolean sendSleep(DeviceSpecificSettingsHandler handler) {
        final GBDevice device = handler.getDevice();
        if (!device.isInitialized()) {
            LOG.warn("SleepTest device: {}", device.getState());
            toast(handler.getContext(), R.string.device_not_connected, Toast.LENGTH_LONG, GB.ERROR);
            return false;
        }

        final DevicePrefs prefs = GBApplication.getDevicePrefs(device);
        final LocalTime wakeTime = prefs.getLocalTime("garmin_experimental_sleep_WakeTime", "07:00");
        LOG.debug("SleepTest wakeTime: {}", wakeTime);
        final long wake = wakeTime.getHour() * 3600L + wakeTime.getMinute() * 60L + wakeTime.getSecond();
        LOG.debug("SleepTest wake: {}", wake);

        final LocalTime sleepTime = prefs.getLocalTime("garmin_experimental_sleep_SleepTime", "20:00");
        LOG.debug("SleepTest sleepTime {}", sleepTime);
        final long sleep = sleepTime.getHour() * 3600L + sleepTime.getMinute() * 60L + sleepTime.getSecond();
        LOG.debug("SleepTest sleep: {}", sleep);

        final List<RecordData> recordData = new ArrayList<>(10);
        final long now = System.currentTimeMillis() / 1000L;

        final FitFileId.Builder fb = new FitFileId.Builder();
        fb.setSerialNumber(1L);
        fb.setTimeCreated(now);
        fb.setManufacturer(1);
        fb.setProduct(65534);
        fb.setNumber(1);
        fb.setType(FileType.FILETYPE.SETTINGS);
        fb.setProductName("GBSleepTest");
        recordData.add(fb.build());

        final FitUserProfile.Builder ub = new FitUserProfile.Builder();
        ub.setWakeTime(wake);
        ub.setSleepTime(sleep);
        recordData.add(ub.build());

        final FitFile fitFile = new FitFile(recordData);
        final byte[] fitBytes = fitFile.getOutgoingMessage();

        final Uri uri = Uri.parse("fake://SleepTest");
        final Bundle options = new Bundle();
        options.putByteArray(AbstractDeviceSupport.BUNDLE_EXTRA_INSTALL_BYTES, fitBytes);
        options.putString(AbstractDeviceSupport.BUNDLE_EXTRA_INSTALL_TASK_NAME, "configure SleepTest times");
        LOG.debug("send SleepTest to device");
        GBApplication.deviceService(device).onInstallApp(uri, options);
        return true;
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    public static final Creator<GarminSettingsCustomizer> CREATOR = new Creator<>() {
        @Override
        public GarminSettingsCustomizer createFromParcel(final Parcel in) {
            return new GarminSettingsCustomizer();
        }

        @Override
        public GarminSettingsCustomizer[] newArray(final int size) {
            return new GarminSettingsCustomizer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
    }

}
