package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.a30i;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class SoundcoreA30iDeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<SoundcoreA30iProtocol> {

    public static final UUID UUID_DEVICE_CTRL = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3958");

    public SoundcoreA30iDeviceSupport() {
        addSupportedService(UUID_DEVICE_CTRL);
    }

    @Override
    protected SoundcoreA30iProtocol createDeviceProtocol() {
        return new SoundcoreA30iProtocol(getDevice());
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        // 1. Request device info (battery status, firmware version, serial number, device settings).
        // Note: This command is sent two times by the official app, the response seems to be
        // the same.
        builder.write(mDeviceProtocol.encodeDeviceInfoRequest());

        // For documentation: 2. Request more information from the device. Currently unknown what the
        // additional information are, but the response contains more bytes than the answer from
        // the first request.
        // builder.write(mDeviceProtocol.encodeSessionInitMoreInfoRequest());

        // 3. Tell the device it is successfully connected to the app
        // – the device ACKs with an empty response
        builder.write(mDeviceProtocol.encodeSessionInitRequest());

        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }
}