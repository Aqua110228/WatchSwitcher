package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu4Coordinator;

public class GarminD2AirX15Coordinator extends GarminVenu4Coordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^D2 Air X15( - \\d+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_d2_air_x15;
    }
}
