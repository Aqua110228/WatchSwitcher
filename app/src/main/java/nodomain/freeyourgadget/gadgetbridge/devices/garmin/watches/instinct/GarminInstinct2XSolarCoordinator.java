package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.GarminWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class GarminInstinct2XSolarCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        // Allow ending both with "Sol" (#3063) and "Solar" (reported on Matrix).
        return Pattern.compile("^Instinct 2X Sol(ar)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_instinct_2x_solar;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull final GBDevice device) {
        return true;
    }
}
