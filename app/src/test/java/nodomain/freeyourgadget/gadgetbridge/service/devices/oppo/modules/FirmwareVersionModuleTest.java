package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class FirmwareVersionModuleTest extends TestBase {
    @Test
    public void testDecodeRet() {
        final FirmwareVersionModule module = new FirmwareVersionModule(GBApplication.getContext());

        GBDeviceEventVersionInfo oppoEvent = module.decodeRet(GB.hexStringToByteArray("000a312c312c312c312c322c3136302c312c332c3838382c312c342c302c322c312c312c322c322c3136302c322c332c3838382c322c342c302c332c312c312c332c322c38323700"));
        Assert.assertEquals("160.160.827", oppoEvent.fwVersion);

        GBDeviceEventVersionInfo air2Event = module.decodeRet(GB.hexStringToByteArray("0009312C312C302C312C322C3134322C312C342C302C322C312C382C322C322C3134322C322C342C302C332C312C33332C332C322C3132392C332C342C30"));
        Assert.assertEquals("142.142.129", air2Event.fwVersion);

        GBDeviceEventVersionInfo realmeEvent = module.decodeRet(GB.hexStringToByteArray("0003312c322c312e312e302e37352c322c322c312e312e302e37352c332c322c303031"));
        Assert.assertEquals("1.1.0.75", realmeEvent.fwVersion);
    }
}
