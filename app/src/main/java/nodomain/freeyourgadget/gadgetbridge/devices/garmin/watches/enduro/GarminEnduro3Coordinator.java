package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.enduro;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.GarminWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class GarminEnduro3Coordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Enduro 3$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_enduro_3;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull final GBDevice device) {
        return true;
    }
}
