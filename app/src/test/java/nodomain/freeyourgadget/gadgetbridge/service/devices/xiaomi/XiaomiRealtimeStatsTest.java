/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services.XiaomiHealthService;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The band has a single realtime stats stream, started by the first consumer and stopped by the
 * last one to release it.
 */
public class XiaomiRealtimeStatsTest extends TestBase {

    private static final int CMD_REALTIME_STATS_START = 45;
    private static final int CMD_REALTIME_STATS_STOP = 46;

    private XiaomiSupport support;
    private XiaomiHealthService health;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        support = Mockito.mock(XiaomiSupport.class);
        Mockito.when(support.getDevice()).thenReturn(createDummyGDevice("00:11:22:33:44:55"));
        health = new XiaomiHealthService(support);
    }

    private int count(final int subtype) {
        final ArgumentCaptor<XiaomiProto.Command> captor =
                ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support, Mockito.atLeast(0))
                .sendCommand(Mockito.anyString(), captor.capture());
        int count = 0;
        for (final XiaomiProto.Command command : captor.getAllValues()) {
            if (command.getSubtype() == subtype) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void firstConsumerStartsTheStream() {
        health.enableRealtimeStats(true);

        Assert.assertEquals(1, count(CMD_REALTIME_STATS_START));
        Assert.assertEquals(0, count(CMD_REALTIME_STATS_STOP));
    }

    @Test
    public void repeatedEnableDoesNotRestart() {
        health.enableRealtimeStats(true);
        health.enableRealtimeStats(true);

        Assert.assertEquals(1, count(CMD_REALTIME_STATS_START));
    }

    @Test
    public void lastConsumerStopsTheStream() {
        health.enableRealtimeStats(true);
        health.enableRealtimeStats(false);

        Assert.assertEquals(1, count(CMD_REALTIME_STATS_START));
        Assert.assertEquals(1, count(CMD_REALTIME_STATS_STOP));
    }

    @Test
    public void closingTheChartsKeepsAHeartRateMeasurementRunning() {
        health.onHeartRateTest();
        health.enableRealtimeStats(true);
        health.enableRealtimeStats(false);

        Assert.assertEquals(1, count(CMD_REALTIME_STATS_START));
        Assert.assertEquals(0, count(CMD_REALTIME_STATS_STOP));
    }
}
