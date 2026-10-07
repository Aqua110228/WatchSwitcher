/*  Copyright (C) 2026

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

package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.requests;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.Earphones;
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiSupportProvider;

/**
 * Starts or stops the sound on the left earbud, the right earbud, or both.
 */
public class SetFindHeadphonesRequest extends Request {
    private static final byte SIDE_LEFT = 0x00;
    private static final byte SIDE_RIGHT = 0x01;

    private final boolean start;
    private final FindDeviceTarget target;

    public SetFindHeadphonesRequest(final HuaweiSupportProvider supportProvider,
                                    final boolean start,
                                    @NonNull final FindDeviceTarget target) {
        super(supportProvider);
        this.serviceId = Earphones.id;
        this.commandId = Earphones.FindHeadphones.id;
        this.start = start;
        this.target = target;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        final boolean left = start && (target == FindDeviceTarget.ALL || target == FindDeviceTarget.LEFT);
        final boolean right = start && (target == FindDeviceTarget.ALL || target == FindDeviceTarget.RIGHT);

        try {
            final List<byte[]> requests = new ArrayList<>();
            requests.addAll(new Earphones.FindHeadphones.Request(paramsProvider, SIDE_LEFT, !left).serialize());
            requests.addAll(new Earphones.FindHeadphones.Request(paramsProvider, SIDE_RIGHT, !right).serialize());
            return requests;
        } catch (final HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
