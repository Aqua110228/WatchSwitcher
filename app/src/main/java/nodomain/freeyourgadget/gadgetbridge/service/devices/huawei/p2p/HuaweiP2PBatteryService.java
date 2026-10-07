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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiP2PManager;

/**
 * Handles the battery/charging events pushed by the watch over the P2P channel
 * (service 0x34). The watch app "com.huawei.BatteryEventMgr" sends a JSON payload
 * whenever the charging state changes, e.g.:
 * {"type":"battery","wearingStatus":"OFF","chargingStatus":"ON","powerStatus":"MIDDLE","powerValue":87,"reason":"chargingStatus"}
 * The standard battery protocol does not report the charging state, so this is the
 * only reliable way to know when the device is charging.
 */
public class HuaweiP2PBatteryService extends HuaweiBaseP2PService {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiP2PBatteryService.class);

    public static final String MODULE = "com.huawei.BatteryEventMgr";

    public HuaweiP2PBatteryService(HuaweiP2PManager manager) {
        super(manager);
        LOG.info("HuaweiP2PBatteryService");
    }

    @Override
    public String getModule() {
        return HuaweiP2PBatteryService.MODULE;
    }

    @Override
    public String getPackage() {
        return "com.huawei.BatteryEventMgr";
    }

    @Override
    public String getFingerprint() {
        return "SystemApp";
    }

    @Override
    public void registered() {
    }

    @Override
    public void unregister() {
    }

    @Override
    public void handleData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        final String json = new String(data, StandardCharsets.UTF_8);
        LOG.debug("HuaweiP2PBatteryService handleData: {}", json);

        try {
            final JSONObject obj = new JSONObject(json);

            if (!"battery".equals(obj.optString("type"))) {
                return;
            }

            final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();

            if (obj.has("chargingStatus")) {
                batteryInfo.state = "ON".equalsIgnoreCase(obj.getString("chargingStatus")) ?
                        BatteryState.BATTERY_CHARGING :
                        BatteryState.BATTERY_NORMAL;
            } else {
                batteryInfo.state = BatteryState.BATTERY_NORMAL;
            }

            if (obj.has("powerValue")) {
                batteryInfo.level = obj.getInt("powerValue");
            } else {
                // No level in this event: keep whatever the device already reports.
                batteryInfo.level = manager.getSupportProvider().getDevice().getBatteryLevel(0);
            }

            manager.getSupportProvider().evaluateGBDeviceEvent(batteryInfo);
        } catch (JSONException e) {
            LOG.error("Failed to parse battery event JSON: {}", json, e);
        }
    }

    public static HuaweiP2PBatteryService getRegisteredInstance(HuaweiP2PManager manager) {
        return (HuaweiP2PBatteryService) manager.getRegisteredService(HuaweiP2PBatteryService.MODULE);
    }
}
