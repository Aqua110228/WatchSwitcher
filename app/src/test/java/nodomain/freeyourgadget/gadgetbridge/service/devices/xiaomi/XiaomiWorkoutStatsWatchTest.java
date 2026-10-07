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

import android.os.Looper;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.Shadows;

import java.time.Duration;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatchSport;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services.XiaomiHealthService;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * The live workout stats exchanged while the Sleep as Android synthetic workout is open: the
 * phone's push on subtype 49, the watch's own copy pushed unsolicited on subtype 50, and the
 * timezone the workout status on subtype 26 opens the session with.
 */
public class XiaomiWorkoutStatsWatchTest extends TestBase {

    private static final int COMMAND_TYPE = 8;
    private static final int CMD_WORKOUT_WATCH_STATUS = 26;
    private static final int CMD_WORKOUT_STATS_PHONE = 49;
    private static final int CMD_WEAR_SPORT_DATA_V2A = 50;

    private static final long FULL_INTERVAL_MS = 1_000L;
    private static final long IDLE_INTERVAL_MS = 5_000L;
    private static final long ACTIVE_WINDOW_MS = 10_000L;

    private static final int HEART_RATE_UNKNOWN = 255;

    private XiaomiSupport support;
    private XiaomiHealthService health;
    private SleepAsAndroidSender sender;
    private TimeZone defaultTimeZone;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        support = Mockito.mock(XiaomiSupport.class);
        Mockito.when(support.getDevice()).thenReturn(createDummyGDevice("00:11:22:33:44:55"));
        sender = Mockito.mock(SleepAsAndroidSender.class);
        health = new XiaomiHealthService(support);
        health.setSleepAsAndroidSender(sender);
        defaultTimeZone = TimeZone.getDefault();
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone);
    }

    private void idle(final long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void openSession() {
        health.startRawSensor();
        idle(0);
    }

    private void deliverStats(final WorkoutStatsWatch.Builder stats) {
        health.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(COMMAND_TYPE)
                .setSubtype(CMD_WEAR_SPORT_DATA_V2A)
                .setHealth(Health.newBuilder().setWorkoutStatsWatch(stats))
                .build());
    }

    private List<XiaomiProto.Command> sentStats() {
        final ArgumentCaptor<XiaomiProto.Command> captor =
                ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support, Mockito.atLeast(0))
                .sendCommand(Mockito.anyString(), captor.capture());
        return captor.getAllValues().stream()
                .filter(cmd -> cmd.getSubtype() == CMD_WORKOUT_STATS_PHONE)
                .collect(Collectors.toList());
    }

    /** The timezone the first workout status the phone sent carried. */
    private WorkoutStatusWatchSport timezoneSentToTheBand() {
        final ArgumentCaptor<XiaomiProto.Command> captor =
                ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support, Mockito.atLeast(1))
                .sendCommand(Mockito.anyString(), captor.capture());
        return captor.getAllValues().stream()
                .filter(cmd -> cmd.getSubtype() == CMD_WORKOUT_WATCH_STATUS)
                .findFirst()
                .orElseThrow(AssertionError::new)
                .getHealth().getWorkoutStatusWatch().getSportInfo();
    }

    /** The heart rate the last stats packet the phone sent carried. */
    private int heartRateReportedToTheBand() {
        final List<XiaomiProto.Command> sent = sentStats();
        return sent.isEmpty() ? -1 : sent.get(sent.size() - 1).getHealth().getWorkoutStatsPhone().getHeartRate();
    }

    @Test
    public void theStatsArePushedAtFullRateWhileTheScreenIsLikelyOn() {
        openSession();
        Mockito.clearInvocations(support);

        idle(5 * FULL_INTERVAL_MS);

        Assert.assertEquals(5, sentStats().size());
    }

    @Test
    public void theStatsSlowDownOnceTheActiveWindowIsOver() {
        openSession();
        idle(ACTIVE_WINDOW_MS);
        Mockito.clearInvocations(support);

        idle(4 * IDLE_INTERVAL_MS);

        Assert.assertEquals(4, sentStats().size());
    }

    @Test
    public void theStatsStopWithTheSession() {
        openSession();
        health.stopRawSensor();
        Mockito.clearInvocations(support);

        idle(4 * IDLE_INTERVAL_MS);

        Assert.assertTrue(sentStats().isEmpty());
    }

    @Test
    public void theStatsStopWhenTheServiceIsDisposed() {
        openSession();
        health.dispose();
        Mockito.clearInvocations(support);

        idle(4 * IDLE_INTERVAL_MS);

        Assert.assertTrue(sentStats().isEmpty());
    }

    /** UTC+2 is 8 quarter hours, zigzag 16, the value captured from a band in that zone. */
    @Test
    public void aPositiveTimezoneIsSentZigzagEncoded() {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-2"));
        openSession();

        final WorkoutStatusWatchSport timezone = timezoneSentToTheBand();

        Assert.assertEquals(8, timezone.getTzOffsetQuarterHours());
        Assert.assertArrayEquals(GB.hexStringToByteArray("0810"), timezone.toByteArray());
    }

    /** UTC-5 is -20 quarter hours, zigzag 39. */
    @Test
    public void aNegativeTimezoneIsSentZigzagEncoded() {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+5"));
        openSession();

        final WorkoutStatusWatchSport timezone = timezoneSentToTheBand();

        Assert.assertEquals(-20, timezone.getTzOffsetQuarterHours());
        Assert.assertArrayEquals(GB.hexStringToByteArray("0827"), timezone.toByteArray());
    }

    @Test
    public void theHeartRateReachesSleepAsAndroid() {
        openSession();

        deliverStats(WorkoutStatsWatch.newBuilder().setHeartRate(61).setCalories(4));

        Mockito.verify(sender).onHrChanged(61, 0);
    }

    @Test
    public void theHeartRateGoesBackToTheBandWithTheNextStats() {
        openSession();

        deliverStats(WorkoutStatsWatch.newBuilder().setHeartRate(61));
        idle(FULL_INTERVAL_MS);

        Assert.assertEquals(61, heartRateReportedToTheBand());
    }

    @Test
    public void aReadingOfZeroIsNotAHeartRate() {
        openSession();

        deliverStats(WorkoutStatsWatch.newBuilder().setHeartRate(0).setCalories(4));
        deliverStats(WorkoutStatsWatch.newBuilder().setCalories(5));
        idle(FULL_INTERVAL_MS);

        Mockito.verify(sender, Mockito.never()).onHrChanged(Mockito.anyFloat(), Mockito.anyLong());
        Assert.assertEquals(HEART_RATE_UNKNOWN, heartRateReportedToTheBand());
    }

    @Test
    public void aWorkoutStartedOnTheWatchFeedsNothingToSleepAsAndroid() {
        deliverStats(WorkoutStatsWatch.newBuilder().setHeartRate(61));

        Mockito.verify(sender, Mockito.never()).onHrChanged(Mockito.anyFloat(), Mockito.anyLong());
    }

    @Test
    public void theStatsAreNotAnsweredOnTheWire() {
        openSession();
        Mockito.clearInvocations(support);

        deliverStats(WorkoutStatsWatch.newBuilder().setHeartRate(61));

        Mockito.verify(support, Mockito.never())
                .sendCommand(Mockito.anyString(), Mockito.any(XiaomiProto.Command.class));
    }

    /**
     * A frame a Mi Band 10 pushed during a Sleep as Android session: 55 bpm, 651 kcal into the
     * workout, no steps and no distance.
     */
    @Test
    public void aCapturedFrameDecodesAndFeedsSleepAsAndroid() throws Exception {
        final XiaomiProto.Command cmd = XiaomiProto.Command.parseFrom(
                GB.hexStringToByteArray("08081032520CD202090837108B0518002000"));

        Assert.assertEquals(COMMAND_TYPE, cmd.getType());
        Assert.assertEquals(CMD_WEAR_SPORT_DATA_V2A, cmd.getSubtype());

        final WorkoutStatsWatch stats = cmd.getHealth().getWorkoutStatsWatch();
        Assert.assertEquals(55, stats.getHeartRate());
        Assert.assertEquals(651, stats.getCalories());
        Assert.assertEquals(0, stats.getSteps());
        Assert.assertEquals(0, stats.getDistance());

        openSession();
        health.handleCommand(cmd);

        Mockito.verify(sender).onHrChanged(55, 0);
    }
}
