/*  Copyright (C) 2024 Arjan Schrijver, Daniele Gobbetti

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
package nodomain.freeyourgadget.gadgetbridge.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import org.slf4j.Logger

abstract class AbstractHeadphoneBLEDeviceSupport(logger: Logger) :
    AbstractBTLESingleDeviceSupport(logger), HeadphoneHelper.Callback {

    private lateinit var headphoneHelper: HeadphoneHelper

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        headphoneHelper = HeadphoneHelper(this.context, device, this)
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            if (::headphoneHelper.isInitialized) {
                headphoneHelper.dispose()
            }
            super.dispose()
        }
    }

    override fun onSetCallState(callSpec: CallSpec) {
        headphoneHelper.onSetCallState(callSpec)
    }

    override fun onNotification(notificationSpec: NotificationSpec) {
        headphoneHelper.onNotification(notificationSpec)
    }

    override fun onSendConfiguration(config: String) {
        if (!headphoneHelper.onSendConfiguration(config)) {
            super.onSendConfiguration(config)
        }
    }
}