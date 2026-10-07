package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix8ProCoordinator;

public class GarminD2Mach2ProCoordinator extends GarminFenix8ProCoordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^D2 Mach 2 Pro( - \\d+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_d2_mach_2_pro;
    }
}
