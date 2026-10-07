package nodomain.freeyourgadget.gadgetbridge.devices.soundcore.a30i;


import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.a30i.SoundcoreA30iDeviceSupport;

public class SoundcoreA30iCoordinator extends AbstractDeviceCoordinator {

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_soundcore_a30i;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_galaxy_buds_3_pro;
    }

    @Override
    public String getManufacturer() {
        return "Anker";
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("soundcore A30i");
    }

    @Override
    public int getBondingStyle(){
        return BONDING_STYLE_NONE;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull GBDevice device) {
        return SoundcoreA30iDeviceSupport.class;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.EARBUDS;
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings deviceSpecificSettings = new DeviceSpecificSettings();
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_soundcore_a30i);
        return deviceSpecificSettings;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 2;
    }

    @Override
    public BatteryConfig[] getBatteryConfig(final GBDevice device) {
        BatteryConfig battery_l = new BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud);
        BatteryConfig battery_r = new BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud);
        return new BatteryConfig[]{battery_l, battery_r};
    }
}
