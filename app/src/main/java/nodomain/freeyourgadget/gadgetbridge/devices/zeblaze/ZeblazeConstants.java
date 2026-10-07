/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze;

import java.util.UUID;

/**
 * GATT UUIDs and command ids for the "ZH_SDK" protocol used by the Zeblaze
 * Beyond 3 Pro's official app. Reverse-engineered from a live capture of the
 * app's own on-device plaintext debug log and BLE HCI trace against a real
 * device (no reverse-engineering of the app's compiled code involved). The
 * service/characteristic UUIDs happen to share Xiaomi's custom GATT UUID
 * base, but the wire protocol carried over them is this watch's own,
 * unrelated to and incompatible with Xiaomi's Mi Fitness protocol.
 */
public class ZeblazeConstants {
    public static final UUID UUID_SERVICE_ZH_SDK = UUID.fromString("16186f00-0000-1000-8000-00807f9b34fb");
    // Watch -> app command responses arrive here (watch writes, app reads via notify).
    public static final UUID UUID_CHARACTERISTIC_COMMAND_READ = UUID.fromString("16186f01-0000-1000-8000-00807f9b34fb");
    // App -> watch commands are sent here (app writes, watch acks via notify).
    public static final UUID UUID_CHARACTERISTIC_COMMAND_WRITE = UUID.fromString("16186f02-0000-1000-8000-00807f9b34fb");

    // Single-byte command id, sent as the protobuf payload {1: commandId}.
    // Bundles firmware version, MAC, serial number, and battery status in one response.
    public static final int CMD_GET_DEVICE_INFO = 32;

    private ZeblazeConstants() {
    }
}
