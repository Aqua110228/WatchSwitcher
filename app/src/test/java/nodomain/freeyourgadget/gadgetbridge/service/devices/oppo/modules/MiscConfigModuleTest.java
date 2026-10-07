package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.MiscConfigType;

public class MiscConfigModuleTest extends TestBase {
    @Test
    public void testDecodeRetSingleType() {
        final MiscConfigModule module = new MiscConfigModule(GBApplication.getContext());

        final Map<MiscConfigType, Boolean> resultWithTrue = module.decodeRet(GB.hexStringToByteArray("00011801"));
        Assert.assertEquals(1, resultWithTrue.size());
        Assert.assertTrue(resultWithTrue.containsKey(MiscConfigType.LDAC));
        Assert.assertEquals(true, resultWithTrue.get(MiscConfigType.LDAC));

        final Map<MiscConfigType, Boolean> resultWithFalse = module.decodeRet(GB.hexStringToByteArray("00011800"));
        Assert.assertEquals(1, resultWithFalse.size());
        Assert.assertTrue(resultWithFalse.containsKey(MiscConfigType.LDAC));
        Assert.assertEquals(false, resultWithFalse.get(MiscConfigType.LDAC));
    }

    @Test
    public void testDecodeRetMultiType() {
        final MiscConfigModule module = new MiscConfigModule(GBApplication.getContext());

        final Map<MiscConfigType, Boolean> resultWithTrue = module.decodeRet(GB.hexStringToByteArray("000218010601"));
        Assert.assertEquals(2, resultWithTrue.size());
        Assert.assertTrue(resultWithTrue.containsKey(MiscConfigType.LDAC));
        Assert.assertEquals(true, resultWithTrue.get(MiscConfigType.LDAC));
        Assert.assertTrue(resultWithTrue.containsKey(MiscConfigType.GAME_MODE));
        Assert.assertEquals(true, resultWithTrue.get(MiscConfigType.GAME_MODE));

        final Map<MiscConfigType, Boolean> resultWithFalse = module.decodeRet(GB.hexStringToByteArray("000218000600"));
        Assert.assertEquals(2, resultWithFalse.size());
        Assert.assertTrue(resultWithFalse.containsKey(MiscConfigType.LDAC));
        Assert.assertEquals(false, resultWithFalse.get(MiscConfigType.LDAC));
        Assert.assertTrue(resultWithFalse.containsKey(MiscConfigType.GAME_MODE));
        Assert.assertEquals(false, resultWithFalse.get(MiscConfigType.GAME_MODE));

        final Map<MiscConfigType, Boolean> resultWithMixed = module.decodeRet(GB.hexStringToByteArray("000218000601"));
        Assert.assertEquals(2, resultWithMixed.size());
        Assert.assertTrue(resultWithMixed.containsKey(MiscConfigType.LDAC));
        Assert.assertEquals(false, resultWithMixed.get(MiscConfigType.LDAC));
        Assert.assertTrue(resultWithMixed.containsKey(MiscConfigType.GAME_MODE));
        Assert.assertEquals(true, resultWithMixed.get(MiscConfigType.GAME_MODE));
    }
}
