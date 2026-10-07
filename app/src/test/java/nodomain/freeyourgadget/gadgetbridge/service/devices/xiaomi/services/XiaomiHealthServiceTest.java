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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.HeartRate;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;

/**
 * The band reports its heart rate measurement interval in minutes, as the protocol comment on
 * {@code HeartRate.interval} records, but the preference it is stored in holds seconds. Storing
 * the reply unconverted leaves a value that matches no entry in the list, so the setting shows
 * as unset after every reconnection.
 */
public class XiaomiHealthServiceTest {
    /** Subtype of the heart rate config reply, {@code CMD_CONFIG_HEART_RATE_GET}. */
    private static final int HEART_RATE_CONFIG_REPLY = 10;

    @Test
    public void reportedIntervalIsStoredInSeconds() {
        assertStoredInterval(10, "600");
    }

    @Test
    public void everyIntervalTheBandCanReportMatchesAPreferenceEntry() {
        // the values the protocol comment lists, in minutes
        assertStoredInterval(1, "60");
        assertStoredInterval(10, "600");
        assertStoredInterval(30, "1800");
    }

    @Test
    public void zeroMeansSmartAndIsNotAnInterval() {
        assertStoredInterval(0, "-1");
    }

    private void assertStoredInterval(final int reportedMinutes, final String expectedPreference) {
        final List<GBDeviceEvent> events = new ArrayList<>();
        final XiaomiSupport support = mock(XiaomiSupport.class);
        doAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        }).when(support).evaluateGBDeviceEvent(any(GBDeviceEvent.class));

        final XiaomiHealthService service = new XiaomiHealthService(support);

        service.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(XiaomiHealthService.COMMAND_TYPE)
                .setSubtype(HEART_RATE_CONFIG_REPLY)
                .setHealth(Health.newBuilder()
                        .setHeartRate(HeartRate.newBuilder()
                                .setDisabled(false)
                                .setInterval(reportedMinutes)))
                .build());

        for (final GBDeviceEvent event : events) {
            if (event instanceof GBDeviceEventUpdatePreferences) {
                final Object stored = ((GBDeviceEventUpdatePreferences) event)
                        .preferences.get(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL);
                assertEquals(
                        "band reported " + reportedMinutes + " minutes",
                        expectedPreference,
                        stored
                );
                return;
            }
        }

        fail("the heart rate config reply did not update any preference");
    }
}
