/*  Copyright (C) 2024 José Rebelo
    Copyright (C) 2026 NTeditor

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo;

import androidx.annotation.NonNull;

import java.util.UUID;

public enum OppoUuid {
    STANDARD_SPP("00001101-0000-1000-8000-00805f9b34fb"),
    VENDOR_RFCOMM("0000079a-d102-11e1-9b23-00025b00a5a5"),
    ;

    private final UUID uuid;

    OppoUuid(@NonNull final String uuid) {
        this.uuid = UUID.fromString(uuid);
    }

    @NonNull
    public UUID getUuid() {
        return uuid;
    }
}
