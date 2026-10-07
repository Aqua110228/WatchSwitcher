/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.datasync;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiSupportProvider;

public class HuaweiDataSyncWheelchairService implements HuaweiDataSyncCommon.DataCallback {
    private static final Logger LOG = LoggerFactory.getLogger(HuaweiDataSyncWheelchairService.class);

    private final HuaweiSupportProvider support;

    public HuaweiDataSyncWheelchairService(final HuaweiSupportProvider support) {
        this.support = support;
        support.getHuaweiDataSyncManager().registerCallback(PACKAGE, this);
    }

    public static final String SRC_PACKAGE = "hw.unitedevice.wheelchair";
    public static final String PACKAGE = "hw.watch.wheelchair";
    private static final int CONFIG_ID = 900500039;
    private static final byte CONFIG_ACTION_SET = 1;
    private static final String WHEELCHAIR_SWITCH = "wheelchairSwitch";

    public boolean setWheelchairMode(final boolean enabled) {
        return sendWheelchairMode(enabled, System.currentTimeMillis());
    }

    /**
     * Reconciles a locally cached value with the watch. A zero timestamp makes the watch return
     * its newer value instead of applying the cached value, as observed in Huawei Health.
     */
    public boolean requestWheelchairModeStatus(final boolean lastKnownEnabled) {
        return sendWheelchairMode(lastKnownEnabled, 0);
    }

    private boolean sendWheelchairMode(final boolean enabled, final long timestamp) {
        try {
            final JSONObject data = new JSONObject();
            data.put(WHEELCHAIR_SWITCH, enabled ? "1" : "0");

            final HuaweiDataSyncCommon.ConfigData config = new HuaweiDataSyncCommon.ConfigData();
            config.configId = CONFIG_ID;
            config.configAction = CONFIG_ACTION_SET;
            config.configData = data.toString().getBytes(StandardCharsets.UTF_8);
            config.configUnknown = timestamp;

            final HuaweiDataSyncCommon.ConfigCommandData command = new HuaweiDataSyncCommon.ConfigCommandData();
            command.setConfigDataList(Collections.singletonList(config));
            return support.getHuaweiDataSyncManager()
                    .sendConfigCommand(SRC_PACKAGE, PACKAGE, command);
        } catch (final JSONException e) {
            LOG.error("Failed to create wheelchair mode configuration", e);
            return false;
        }
    }

    @Override
    public void onConfigCommand(final HuaweiDataSyncCommon.ConfigCommandData data) {
        if (data.getConfigDataList() == null) {
            return;
        }

        for (final HuaweiDataSyncCommon.ConfigData config : data.getConfigDataList()) {
            if (config.configId != CONFIG_ID || config.configData == null || config.configData.length == 0) {
                continue;
            }

            final String json = new String(config.configData, StandardCharsets.UTF_8);
            LOG.debug("HuaweiDataSyncWheelchairService onConfigCommand: {}", json);

            try {
                final JSONObject obj = new JSONObject(json);
                final String value = obj.optString(WHEELCHAIR_SWITCH, null);
                if (!"0".equals(value) && !"1".equals(value)) {
                    continue;
                }

                GBApplication.getDeviceSpecificSharedPrefs(support.getDevice().getAddress())
                        .edit()
                        .putBoolean(HuaweiConstants.PREF_HUAWEI_WHEELCHAIR_MODE, "1".equals(value))
                        .apply();
            } catch (final JSONException e) {
                LOG.error("Failed to parse wheelchair mode JSON: {}", json, e);
            }
        }
    }

    @Override
    public void onEventCommand(final HuaweiDataSyncCommon.EventCommandData data) {
    }

    @Override
    public void onDataCommand(final HuaweiDataSyncCommon.DataCommandData data) {
    }

    @Override
    public void onDictDataCommand(final HuaweiDataSyncCommon.DictDataCommandData data) {
    }
}
