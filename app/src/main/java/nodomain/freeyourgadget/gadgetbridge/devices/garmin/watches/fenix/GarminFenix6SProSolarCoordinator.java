package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.GarminWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class GarminFenix6SProSolarCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^fenix 6S Pro Solar$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_fenix_6s_pro_solar;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull final GBDevice device) {
        return true;
    }
}
