package nodomain.freeyourgadget.gadgetbridge.devices.earfun;

import android.bluetooth.BluetoothDevice;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.earfun.EarFunDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public abstract class AbstractEarFunCoordinator extends AbstractBLClassicDeviceCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEarFunCoordinator.class);

    private static final int EARFUN_VENDOR_ID = 0x5a70;

    @Override
    public String getManufacturer() {
        return "EarFun";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return EarFunDeviceSupport.class;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_BOND;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_nothingear;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.EARBUDS;
    }

    protected byte[] getProductId(GBDeviceCandidate candidate) {
        BluetoothDevice dev = candidate.getDevice();
        SparseArray<byte[]> manufData = candidate.getManufacturerSpecificData();

        if (dev == null || manufData == null) {
            return null;
        }

        byte[] earfunData = manufData.get(EARFUN_VENDOR_ID);

        if (earfunData == null) {
            return null;
        }

        byte[] mac = GB.hexStringToByteArray(dev.getAddress().replace(":", ""));
        byte[] addr = Arrays.copyOfRange(mac, 2, mac.length);
        int index = -1;

        for (int i = 0; i <= earfunData.length - addr.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(earfunData, i, i + addr.length), addr)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return null;
        }

        byte[] pid = Arrays.copyOfRange(earfunData, index + addr.length, earfunData.length);

        LOG.debug("Found EarFun device with product ID {}", StringUtils.bytesToHex(pid));

        return pid;
    }
}
