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

import java.util.Collections;

import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiSupportProvider;

/**
 * Sends observed configuration synchronization requests to the syncdevice DataSync service.
 */
public class HuaweiDataSyncSyncDeviceService {
    private static final String SRC_PACKAGE = "hw.unitedevice.syncdevice";
    private static final String PACKAGE = "syncdevice";
    private static final int CONFIG_ID = 900100003;
    private static final byte CONFIG_ACTION = 3;
    private static final byte[] CONFIG_DATA = {0x08, 0x01, 0x01};

    private final HuaweiSupportProvider support;

    public HuaweiDataSyncSyncDeviceService(final HuaweiSupportProvider support) {
        this.support = support;
    }

    public boolean requestConfigurationSync() {
        final HuaweiDataSyncCommon.ConfigData config = new HuaweiDataSyncCommon.ConfigData();
        config.configId = CONFIG_ID;
        config.configAction = CONFIG_ACTION;
        config.configData = CONFIG_DATA;

        final HuaweiDataSyncCommon.ConfigCommandData command = new HuaweiDataSyncCommon.ConfigCommandData();
        command.setConfigDataList(Collections.singletonList(config));
        return support.getHuaweiDataSyncManager().sendConfigCommand(SRC_PACKAGE, PACKAGE, command);
    }
}
