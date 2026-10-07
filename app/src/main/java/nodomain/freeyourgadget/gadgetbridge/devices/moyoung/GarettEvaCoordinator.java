/*  Copyright (C) 2026 Łukasz Zieliński

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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericBloodPressureSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.MoyoungBloodPressureSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.MoyoungStressSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.MoyoungBloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;

public class GarettEvaCoordinator extends AbstractMoyoungDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Garett Eva$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garett_eva;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_banglejs;
    }

    @Override
    public String getManufacturer() {
        return "Garett";
    }

    @Override
    public int getMtu() {
        return 508;
    }

    @Override
    public boolean supportsCalendarEvents(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public TimeSampleProvider<? extends MoyoungBloodPressureSample> getBloodPressureSampleProvider(
            final GBDevice device, final DaoSession session) {
        return new MoyoungBloodPressureSampleProvider(device, session);
    }
}