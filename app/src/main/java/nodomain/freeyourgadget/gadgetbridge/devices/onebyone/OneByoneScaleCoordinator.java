/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.devices.onebyone;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericWeightSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.onebyone.OneByoneScaleDeviceSupport;

/**
 * Body composition scales made by LeFu (Shenzhen Unique Scales) that advertise as "Health Scale"
 * and speak the proprietary 0xFFF0 protocol. They are sold under several brands, e.g. 1byone
 * (1byone Health app) and Veeway (HealthU+ app); the firmware itself reports "LeFu Scale" as the
 * manufacturer. The protocol is documented in the openScale project (OneByoneHandler, GPLv3).
 */
public class OneByoneScaleCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public Map<AbstractDao<?, ?>, Property> getAllDeviceDao(@NonNull final DaoSession session) {
        final Map<AbstractDao<?, ?>, Property> map = new HashMap<>(1);
        map.put(session.getGenericWeightSampleDao(), GenericWeightSampleDao.Properties.DeviceId);
        return map;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Health Scale", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Override
    public String getManufacturer() {
        return "LeFu";
    }

    @Override
    public TimeSampleProvider<? extends WeightSample> getWeightSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericWeightSampleProvider(device, session);
    }

    @Override
    public boolean supportsWeightMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCharts(@NonNull final GBDevice device) {
        return true;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return OneByoneScaleDeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_onebyone_scale;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miscale;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceKind.SCALE;
    }
}
