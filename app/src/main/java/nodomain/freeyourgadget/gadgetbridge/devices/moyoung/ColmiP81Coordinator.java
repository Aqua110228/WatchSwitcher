package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class ColmiP81Coordinator extends AbstractMoyoungDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^P81c$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_colmi_p81;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_amazfit_bip;
    }

    @Override
    public String getManufacturer() {
        return "Colmi";
    }

    @Override
    public int getMtu() {
        return 508;
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 8;
    }

    @Override
    public int getWorldClocksSlotCount() {
        return 6;
    }

    @Override
    public int getWorldClocksLabelLength() {
        return 30;
    }

    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsStressMeasurement(@NonNull GBDevice device) {
        return true;
    }
}
