package nodomain.freeyourgadget.gadgetbridge.devices.earfun;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.earfun.airpro4.EarFunAirPro4DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.earfun.airpro4.EarFunAirPro4SettingsCustomizer;

public class EarFunAirPro4Coordinator extends AbstractEarFunCoordinator {
    private static final byte[] PRODUCT_ID = new byte[] { 0x45, 0x35 };

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_earfun_air_pro_4;
    }

    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        if (candidate.getName().startsWith("EarFun Air Pro 4")) {
            return true;
        }

        return Arrays.equals(getProductId(candidate), PRODUCT_ID);
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return EarFunAirPro4DeviceSupport.class;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 3;
    }

    @Override
    public BatteryConfig[] getBatteryConfig(final GBDevice device) {
        BatteryConfig battery1 = new BatteryConfig(2, R.drawable.ic_buds_pro_case, R.string.battery_case);
        BatteryConfig battery2 = new BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud);
        BatteryConfig battery3 = new BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud);
        return new BatteryConfig[]{battery1, battery2, battery3};
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings deviceSpecificSettings = new DeviceSpecificSettings();
        // Category Audio Experience
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_header_audio_experience);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_10_band_equalizer);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_air_pro_4_sound_control);

        // Category Audio Quality & Connectivity
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_header_connectivity);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_air_pro_4_audio_quality);

        // Category System Settings
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_header_system_settings);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_air_pro_4_gestures);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_in_ear_detection);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_find_device);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_earfun_device_name);
        final List<Integer> callsAndNotif = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS);
        callsAndNotif.add(R.xml.devicesettings_headphones);
        return deviceSpecificSettings;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new EarFunAirPro4SettingsCustomizer(device);
    }
}
