/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.HeartRate;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The measurement interval crosses the protocol boundary twice: it is sent to the band in
 * minutes and reported back the same way, while the preference holding it is in seconds. The
 * two conversions have to agree, or the value that comes back matches no entry in the list and
 * the setting reads as unset after every reconnection.
 */
public class XiaomiHeartRateIntervalRoundTripTest extends TestBase {
    /** Subtype of the heart rate config reply, {@code CMD_CONFIG_HEART_RATE_GET}. */
    private static final int HEART_RATE_CONFIG_REPLY = 10;

    private GBDevice device;
    private XiaomiSupport support;
    private XiaomiHealthService service;
    private final List<XiaomiProto.Command> sent = new ArrayList<>();
    private final List<GBDeviceEvent> events = new ArrayList<>();

    @Before
    public void setUpService() {
        device = createDummyGDevice("00:00:00:00:00:03");

        support = mock(XiaomiSupport.class);
        when(support.getDevice()).thenReturn(device);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(1));
            return null;
        }).when(support).sendCommand(anyString(), any(XiaomiProto.Command.class));
        doAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        }).when(support).evaluateGBDeviceEvent(any(GBDeviceEvent.class));

        service = new XiaomiHealthService(support);
    }

    @Test
    public void everySelectableIntervalSurvivesTheRoundTrip() {
        for (final HeartRateCapability.MeasurementInterval interval : HeartRateCapability.MeasurementInterval.values()) {
            final int seconds = interval.getIntervalSeconds();
            if (seconds <= 0) {
                // off, smart and continuous are not durations and are carried differently
                continue;
            }
            assertRoundTrip(seconds);
        }
    }

    private void assertRoundTrip(final int seconds) {
        sent.clear();
        events.clear();

        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress())
                .edit()
                .putString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, String.valueOf(seconds))
                .commit();

        // what we send to the band
        service.setHeartRateConfig();
        assertEquals("expected exactly one command for " + seconds + "s", 1, sent.size());
        final HeartRate outgoing = sent.get(0).getHealth().getHeartRate();

        // what the band reports back, echoing the value it was given
        service.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(XiaomiHealthService.COMMAND_TYPE)
                .setSubtype(HEART_RATE_CONFIG_REPLY)
                .setHealth(Health.newBuilder()
                        .setHeartRate(HeartRate.newBuilder()
                                .setDisabled(outgoing.getDisabled())
                                .setInterval(outgoing.getInterval())))
                .build());

        Object restored = null;
        for (final GBDeviceEvent event : events) {
            if (event instanceof GBDeviceEventUpdatePreferences) {
                restored = ((GBDeviceEventUpdatePreferences) event)
                        .preferences.get(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL);
            }
        }

        assertNotNull("no preference update for " + seconds + "s", restored);
        assertEquals(
                "interval of " + seconds + "s came back as something else after the round trip",
                String.valueOf(seconds),
                restored
        );
    }
}
