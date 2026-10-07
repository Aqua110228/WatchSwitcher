/*  Copyright (C) 2023-2024 José Rebelo, Yoran Vulker

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

import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.SystemClock;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivitySample;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationProviderType;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService;
import nodomain.freeyourgadget.gadgetbridge.externalevents.opentracks.OpenTracksController;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.ActivitySyncRequestToday;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.AdvancedMonitoring;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.AxisSensor;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Goal;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.GoalNotification;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.GoalsConfig;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.HeartRate;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.HeartRateAlarmLow;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RawSensorBatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RealTimeStats;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RelaxReminder;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.SpO2;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Spo2AlarmLow;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Spo2Mode;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.StandingReminder;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Stress;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.UserInfo;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.VitalityScore;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutLocation;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenReply;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsPhone;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatchSport;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiPreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileFetcher;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class XiaomiHealthService extends AbstractXiaomiService {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiHealthService.class);

    public static final int COMMAND_TYPE = 8;

    private static final int CMD_SET_USER_INFO = 0;
    private static final int CMD_ACTIVITY_FETCH_TODAY = 1;
    private static final int CMD_ACTIVITY_FETCH_PAST = 2;
    private static final int CMD_ACTIVITY_FETCH_REQUEST = 3;
    private static final int CMD_ACTIVITY_FETCH_ACK = 5;
    private static final int CMD_CONFIG_SPO2_GET = 8;
    private static final int CMD_CONFIG_SPO2_SET = 9;
    private static final int CMD_CONFIG_HEART_RATE_GET = 10;
    private static final int CMD_CONFIG_HEART_RATE_SET = 11;
    private static final int CMD_CONFIG_STANDING_REMINDER_GET = 12;
    private static final int CMD_CONFIG_STANDING_REMINDER_SET = 13;
    private static final int CMD_CONFIG_STRESS_GET = 14;
    private static final int CMD_CONFIG_STRESS_SET = 15;
    private static final int CMD_CONFIG_GOAL_NOTIFICATION_GET = 21;
    private static final int CMD_CONFIG_GOAL_NOTIFICATION_SET = 22;
    private static final int CMD_WORKOUT_WATCH_STATUS = 26;
    private static final int CMD_WORKOUT_WATCH_OPEN = 30;
    private static final int CMD_CONFIG_VITALITY_SCORE_GET = 35;
    private static final int CMD_CONFIG_VITALITY_SCORE_SET = 36;
    private static final int CMD_WORKOUT_LOCATION = 48;
    private static final int CMD_CONFIG_GOALS_GET = 42;
    private static final int CMD_CONFIG_GOALS_SET = 43;
    private static final int CMD_REALTIME_STATS_START = 45;
    private static final int CMD_REALTIME_STATS_STOP = 46;
    private static final int CMD_REALTIME_STATS_EVENT = 47;
    // SaA synthetic workout raw-sensor channels (subtype names per AstroBox FitnessID enum)
    private static final int CMD_WORKOUT_STATS_PHONE = 49;  // FitnessID.PHONE_SPORT_DATA_V2A
    // While a workout is open the watch pushes its own copy of the stats once a second, whatever
    // rate the phone sends at.
    private static final int CMD_WEAR_SPORT_DATA_V2A = 50;  // FitnessID.WEAR_SPORT_DATA_V2A
    private static final int CMD_RAW_SENSOR_BATCH = 53;     // FitnessID.WEAR_SENSOR_DATA
    // Synthetic-sport id used to mark the workout as hidden / non-persistent
    private static final int SAA_SYNTHETIC_SPORT = 810;     // AstroBox SportType.MOTION_SENSING_GAME
    // The band renders these values on its workout screen and blanks it if the stream stops
    // altogether. The full rate is only worth its radio traffic while the screen is likely to be
    // on, which for a sleep session is the first few seconds; the rest of the night runs at the
    // idle rate.
    private static final long WORKOUT_STATS_INTERVAL_MS = 1_000L;
    private static final long WORKOUT_STATS_IDLE_INTERVAL_MS = 5_000L;
    private static final long WORKOUT_STATS_ACTIVE_WINDOW_MS = 10_000L;
    // Reported to the band until a real reading arrives.
    private static final int HEART_RATE_UNKNOWN = 255;

    private static final int WORKOUT_OPEN_OK = 0;
    private static final int WORKOUT_OPEN_NO_PERMISSION = 3;
    // Picked from the versions the watch offers when it asks to open a workout.
    private static final int WORKOUT_PROTOCOL_VERSION = 2;
    private static final int GPS_ACCURACY_HIGH = 2;
    private static final int GPS_ACCURACY_UNKNOWN = 10;

    private static final int GENDER_MALE = 1;
    private static final int GENDER_FEMALE = 2;

    /**
     * The band has a single realtime stats stream shared by everything that needs live readings.
     * It is stopped only once every consumer has released it.
     */
    private enum RealtimeConsumer {
        /** Live activity charts and the device list heart rate badge. */
        UI,
        SLEEP_AS_ANDROID,
        /** A single heart rate measurement, released as soon as a reading arrives. */
        ONE_SHOT,
    }

    private static final int WORKOUT_STARTED = 0;
    private static final int WORKOUT_PAUSED = 1;
    private static final int WORKOUT_RESUMED = 2;
    private static final int WORKOUT_FINISHED = 3;

    // Guarded by itself: consumers are added and released from the handler thread and from the
    // Bluetooth callback thread.
    private final Set<RealtimeConsumer> realtimeConsumers = EnumSet.noneOf(RealtimeConsumer.class);
    private int previousSteps = -1;

    private boolean gpsStarted = false;
    private boolean gpsFixAcquired = false;
    private boolean workoutStarted = false;
    private final Handler gpsTimeoutHandler = new Handler();
    private boolean saaRawSensorActive = false;
    private long saaWorkoutStartedMs = 0;
    private long saaStatsActiveUntilMs = 0;
    private int lastHeartRate = HEART_RATE_UNKNOWN;
    private final Handler saaWorkoutStatsHandler = new Handler();

    private final Set<Integer> currentGoals = new LinkedHashSet<>();
    private final Set<Integer> supportedGoals = new LinkedHashSet<>();

    private static final int GOAL_STEPS = 1; // TODO confirm
    private static final int GOAL_CALORIES = 2; // TODO confirm
    private static final int GOAL_MOVING_TIME = 3;
    private static final int GOAL_STANDING_TIME = 4;

    private final XiaomiActivityFileFetcher activityFetcher = new XiaomiActivityFileFetcher(this);
    private SleepAsAndroidSender sleepAsAndroidSender;

    public void setSleepAsAndroidSender(final SleepAsAndroidSender sender) {
        this.sleepAsAndroidSender = sender;
    }

    public XiaomiHealthService(final XiaomiSupport support) {
        super(support);
    }

    @Override
    public void handleCommand(final XiaomiProto.Command cmd) {
        switch (cmd.getSubtype()) {
            case CMD_SET_USER_INFO:
                LOG.debug("Got user info set ack, status={}", cmd.getStatus());
                return;
            case CMD_ACTIVITY_FETCH_TODAY:
            case CMD_ACTIVITY_FETCH_PAST:
                handleActivityFetchResponse(cmd.getSubtype(), cmd.getHealth().getActivityRequestFileIds().toByteArray());
                return;
            case CMD_CONFIG_SPO2_GET:
                handleSpo2Config(cmd.getHealth().getSpo2());
                return;
            case CMD_CONFIG_SPO2_SET:
                LOG.debug("Got spo2 set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_HEART_RATE_SET:
                LOG.debug("Got heart rate set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_HEART_RATE_GET:
                handleHeartRateConfig(cmd.getHealth().getHeartRate());
                return;
            case CMD_CONFIG_STANDING_REMINDER_GET:
                handleStandingReminderConfig(cmd.getHealth().getStandingReminder());
                return;
            case CMD_CONFIG_STANDING_REMINDER_SET:
                LOG.debug("Got standing reminder set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_STRESS_GET:
                handleStressConfig(cmd.getHealth().getStress());
                return;
            case CMD_CONFIG_STRESS_SET:
                LOG.debug("Got stress set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_GOAL_NOTIFICATION_GET:
                handleGoalNotificationConfig(cmd.getHealth().getGoalNotification());
                return;
            case CMD_CONFIG_GOAL_NOTIFICATION_SET:
                LOG.debug("Got goal notification set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_GOALS_GET:
                handleGoalsConfig(cmd.getHealth().getGoalsConfig());
                return;
            case CMD_CONFIG_GOALS_SET:
                LOG.debug("Got goals config set ack, status={}", cmd.getStatus());
                return;
            case CMD_CONFIG_VITALITY_SCORE_GET:
                handleVitalityScore(cmd.getHealth().getVitalityScore());
                return;
            case CMD_CONFIG_VITALITY_SCORE_SET:
                LOG.debug("Got vitality score set ack, status={}", cmd.getStatus());
                return;
            case CMD_WORKOUT_WATCH_STATUS:
                handleWorkoutStatus(cmd.getHealth().getWorkoutStatusWatch());
                return;
            case CMD_WORKOUT_WATCH_OPEN:
                handleWorkoutOpen(cmd.getHealth().getWorkoutOpenWatch());
                return;
            case CMD_REALTIME_STATS_EVENT:
                handleRealtimeStats(cmd.getHealth().getRealTimeStats());
                return;
            case CMD_RAW_SENSOR_BATCH:
                handleRawSensorBatch(cmd.getHealth().getRawSensorBatch());
                return;
            case CMD_WEAR_SPORT_DATA_V2A:
                handleWorkoutStatsWatch(cmd.getHealth().getWorkoutStatsWatch());
                return;
            case CMD_WORKOUT_STATS_PHONE:
                LOG.debug("Got workout stats echo");
                return;
        }

        LOG.warn("Unknown health command {}", cmd.getSubtype());
    }

    @Override
    public void initialize() {
        gpsStarted = false;
        gpsFixAcquired = false;
        workoutStarted = false;
        synchronized (realtimeConsumers) {
            realtimeConsumers.clear();
        }
        saaRawSensorActive = false;
        gpsTimeoutHandler.removeCallbacksAndMessages(null);

        setUserInfo();
        getSupport().sendCommand("get spo2 config", COMMAND_TYPE, CMD_CONFIG_SPO2_GET);
        getSupport().sendCommand("get heart rate config", COMMAND_TYPE, CMD_CONFIG_HEART_RATE_GET);
        getSupport().sendCommand("get standing reminders config", COMMAND_TYPE, CMD_CONFIG_STANDING_REMINDER_GET);
        getSupport().sendCommand("get stress config", COMMAND_TYPE, CMD_CONFIG_STRESS_GET);
        getSupport().sendCommand("get goal notification config", COMMAND_TYPE, CMD_CONFIG_GOAL_NOTIFICATION_GET);
        getSupport().sendCommand("get goals config", COMMAND_TYPE, CMD_CONFIG_GOALS_GET);
        getSupport().sendCommand("get vitality score config", COMMAND_TYPE, CMD_CONFIG_VITALITY_SCORE_GET);
    }

    @Override
    public void dispose() {
        gpsTimeoutHandler.removeCallbacksAndMessages(null);
        gpsStarted = false;
        gpsFixAcquired = false;
        workoutStarted = false;
        stopWorkoutStatsTicker();
        synchronized (realtimeConsumers) {
            realtimeConsumers.clear();
        }
        saaRawSensorActive = false;
        activityFetcher.dispose();
    }

    @Override
    public boolean onSendConfiguration(final String config, final Prefs prefs) {
        switch (config) {
            case ActivityUser.PREF_USER_HEIGHT_CM:
            case ActivityUser.PREF_USER_WEIGHT_KG:
            case ActivityUser.PREF_USER_DATE_OF_BIRTH:
            case ActivityUser.PREF_USER_GENDER:
            case ActivityUser.PREF_USER_CALORIES_BURNT:
            case ActivityUser.PREF_USER_STEPS_GOAL:
            case ActivityUser.PREF_USER_GOAL_STANDING_TIME_HOURS:
            case ActivityUser.PREF_USER_ACTIVETIME_MINUTES:
                setUserInfo();
                return true;
            case DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_NOTIFICATION:
                sendGoalNotificationConfig();
                return true;
            case DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_SECONDARY:
                sendGoalsConfig();
                return true;
            case DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_7_DAY:
            case DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_DAILY:
                sendVitalityScoreConfig();
                return true;
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_USE_FOR_SLEEP_DETECTION:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_BREATHING_QUALITY_MONITORING:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_ENABLED:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD:
                setHeartRateConfig();
                return true;
            case DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING:
            case DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD:
                setSpo2Config();
                return true;
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE:
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_START:
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_END:
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND:
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_START:
            case DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_END:
                setStandingReminderConfig();
                return true;
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING:
            case DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_RELAXATION_REMINDER:
                setStressConfig();
                return true;
        }

        return false;
    }

    public void setUserInfo() {
        LOG.debug("Setting user info");

        final ActivityUser activityUser = new ActivityUser();
        final LocalDate dateOfBirth = activityUser.getDateOfBirth();
        final int birthYear = dateOfBirth.getYear();
        final byte birthMonth = (byte) dateOfBirth.getMonthValue();
        final byte birthDay = (byte) dateOfBirth.getDayOfMonth();

        final int genderInt = activityUser.getGender() != ActivityUser.GENDER_FEMALE ? GENDER_MALE : GENDER_FEMALE;  // TODO other gender?

        final int age = activityUser.getAge();
        // Compute the approximate max heart rate from the user age
        // TODO max heart rate should be input by the user
        int maxHeartRate = (int) Math.round(age <= 40 ? 220 - age : 207 - 0.7 * age);
        if (maxHeartRate < 100 || maxHeartRate > 220) {
            maxHeartRate = 175;
        }

        final var userInfo = UserInfo.newBuilder()
                .setHeight(activityUser.getHeightCm())
                .setWeight(activityUser.getWeightKg())
                .setBirthday(Integer.parseInt(String.format(Locale.ROOT, "%04d%02d%02d", birthYear, birthMonth, birthDay)))
                .setGender(genderInt)
                .setMaxHeartRate(maxHeartRate)
                .setGoalCalories(activityUser.getCaloriesBurntGoal())
                .setGoalSteps(activityUser.getStepsGoal())
                .setGoalStanding(activityUser.getStandingTimeGoalHours())
                .setGoalMoving(activityUser.getActiveTimeGoalMinutes())
                .build();

        final var health = Health.newBuilder()
                .setUserInfo(userInfo)
                .build();

        getSupport().sendCommand(
                "set user info",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_SET_USER_INFO)
                        .setHealth(health)
                        .build()
        );
    }

    private void handleGoalNotificationConfig(final GoalNotification goalNotification) {
        LOG.debug("Got goal notification config");

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_GOAL_NOTIFICATION, true)
                .withPreference(DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_NOTIFICATION, goalNotification.getEnabled());

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    public void sendGoalNotificationConfig() {
        final boolean enabled = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_NOTIFICATION, false);

        LOG.debug("Setting goal notification enabled = {}", enabled);

        final var goalNotification = GoalNotification.newBuilder()
                .setEnabled(enabled)
                .setUnknown2(1);

        final var health = Health.newBuilder()
                .setGoalNotification(goalNotification)
                .build();

        getSupport().sendCommand(
                "set goal notification config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_GOAL_NOTIFICATION_SET)
                        .setHealth(health)
                        .build()
        );
    }

    private void handleGoalsConfig(final GoalsConfig goalsConfig) {
        LOG.debug("Got goals config");

        currentGoals.clear();
        supportedGoals.clear();

        for (final Goal goal : goalsConfig.getCurrentGoalsList()) {
            currentGoals.add(goal.getId());
        }
        for (final Goal goal : goalsConfig.getSupportedGoalsList()) {
            supportedGoals.add(goal.getId());
        }

        final boolean secondaryGoalSupported = supportedGoals.contains(GOAL_STANDING_TIME) || supportedGoals.contains(GOAL_MOVING_TIME);
        final String secondaryValue = currentGoals.contains(GOAL_MOVING_TIME) ? "active_time" : "standing_time";

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_GOAL_SECONDARY, secondaryGoalSupported)
                .withPreference(DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_SECONDARY, secondaryValue);

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    public void sendGoalsConfig() {
        final String goalSecondary = getDevicePrefs().getString(DeviceSettingsPreferenceConst.PREF_USER_FITNESS_GOAL_SECONDARY, "standing_time");

        LOG.debug("Setting goals config = {}", goalSecondary);

        final var goalsConfig = GoalsConfig.newBuilder();

        for (final Integer currentGoal : currentGoals) {
            if (!currentGoal.equals(GOAL_STANDING_TIME) && !currentGoal.equals(GOAL_MOVING_TIME)) {
                goalsConfig.addCurrentGoals(Goal.newBuilder().setId(currentGoal));
            }
        }

        if (goalSecondary.equals("active_time")) {
            goalsConfig.addCurrentGoals(Goal.newBuilder().setId(GOAL_MOVING_TIME));
        } else {
            goalsConfig.addCurrentGoals(Goal.newBuilder().setId(GOAL_STANDING_TIME));
        }

        for (final Integer supportedGoal : supportedGoals) {
            goalsConfig.addSupportedGoals(Goal.newBuilder().setId(supportedGoal));
        }

        final var health = Health.newBuilder()
                .setGoalsConfig(goalsConfig)
                .build();

        getSupport().sendCommand(
                "set goals config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_GOALS_SET)
                        .setHealth(health)
                        .build()
        );
    }

    private void handleVitalityScore(final VitalityScore vitalityScore) {
        LOG.debug("Got vitality score config");

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_VITALITY_SCORE, true)
                .withPreference(DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_7_DAY, vitalityScore.getSevenDay())
                .withPreference(DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_DAILY, vitalityScore.getDailyProgress());

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    public void sendVitalityScoreConfig() {
        final boolean prefSevenDay = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_7_DAY, false);
        final boolean prefDaily = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_VITALITY_SCORE_DAILY, false);

        LOG.debug("Setting vitality score config, 7day={}, daily={}", prefSevenDay, prefDaily);

        final var vitalityScore = VitalityScore.newBuilder()
                .setSevenDay(prefSevenDay)
                .setDailyProgress(prefDaily)
                .build();

        final var health = Health.newBuilder()
                .setVitalityScore(vitalityScore)
                .build();

        getSupport().sendCommand(
                "set vitality score config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_VITALITY_SCORE_SET)
                        .setHealth(health)
                        .build()
        );
    }

    private void handleSpo2Config(final SpO2 spo2) {
        LOG.debug("Got SpO2 config");

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_SPO2, true)
                // The band reports a numeric mode (0 = off, 1 = sleep-only, 2 = all-day);
                // only all-day maps to the on/off preference in Gadgetbridge.
                // Sleep-only is valid on the wire but is not shown in the on-watch
                // UI nor in the vendor app UI of Mi Band 10, so it reads as off here.
                .withPreference(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, spo2.getMode() == Spo2Mode.SPO2_MODE_ALL_DAY)
                .withPreference(
                        DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD,
                        String.valueOf(spo2.getAlarmLow().getAlarmLowEnabled() ? spo2.getAlarmLow().getAlarmLowThreshold() : 0)
                );

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void setSpo2Config() {
        LOG.debug("Set SpO2 config");

        final Prefs prefs = getDevicePrefs();
        final boolean allDayMonitoring = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, false);
        final int lowAlertThreshold = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD, 0);

        final var spo2alarmLowBuilder = Spo2AlarmLow.newBuilder()
                .setAlarmLowEnabled(lowAlertThreshold != 0);

        if (lowAlertThreshold != 0) {
            spo2alarmLowBuilder.setAlarmLowThreshold(lowAlertThreshold);
        }

        final var spo2 = SpO2.newBuilder()
                .setUnknown1(1)
                .setMode(allDayMonitoring ? Spo2Mode.SPO2_MODE_ALL_DAY : Spo2Mode.SPO2_MODE_OFF)
                .setAlarmLow(spo2alarmLowBuilder);

        getSupport().sendCommand(
                "set spo2 config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_SPO2_SET)
                        .setHealth(Health.newBuilder().setSpo2(spo2))
                        .build()
        );
    }

    private void handleHeartRateConfig(final HeartRate heartRate) {
        LOG.debug("Got heart rate config");

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences();
        if (heartRate.getDisabled()) {
            eventUpdatePreferences.withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, "0");
        } else if (heartRate.getInterval() == 0) {
            // smart
            eventUpdatePreferences.withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, "-1");
        } else {
            // the band reports the interval in minutes, the preference holds seconds
            eventUpdatePreferences.withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, String.valueOf(heartRate.getInterval() * 60));
        }

        eventUpdatePreferences.withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_USE_FOR_SLEEP_DETECTION, heartRate.getAdvancedMonitoring().getEnabled());
        eventUpdatePreferences.withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_BREATHING_QUALITY_MONITORING, heartRate.getBreathingScore() == 1);

        eventUpdatePreferences.withPreference(
                DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD,
                String.valueOf(heartRate.getAlarmHighEnabled() ? heartRate.getAlarmHighThreshold() : 0)
        );

        eventUpdatePreferences.withPreference(
                DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD,
                String.valueOf(heartRate.getHeartRateAlarmLow().getAlarmLowEnabled() ? heartRate.getHeartRateAlarmLow().getAlarmLowThreshold() : 0)
        );

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    public void setHeartRateConfig() {
        final Prefs prefs = getDevicePrefs();

        final boolean sleepDetection = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_USE_FOR_SLEEP_DETECTION, false);
        final boolean sleepBreathingQuality = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_BREATHING_QUALITY_MONITORING, false);
        final int intervalSeconds = prefs.getInt(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, 0);
        final int alertHigh = prefs.getInt(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD, 0);
        final int alertLow = prefs.getInt(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD, 0);

        int intervalMin;
        if (intervalSeconds == -1) {
            // Smart
            intervalMin = 0;
        } else {
            intervalMin = intervalSeconds / 60;
        }

        LOG.debug(
                "Set heart rate config: sleepDetection={}, sleepBreathingQuality={}, intervalSeconds={}, alertHigh={}, alertLow={}",
                sleepDetection,
                sleepBreathingQuality,
                intervalSeconds,
                alertHigh,
                alertLow
        );

        final var heartRate = HeartRate.newBuilder()
                .setDisabled(intervalSeconds == 0)
                .setInterval(intervalMin)
                .setAdvancedMonitoring(AdvancedMonitoring.newBuilder()
                        .setEnabled(sleepDetection))
                .setBreathingScore(sleepBreathingQuality ? 1 : 2)
                .setAlarmHighEnabled(alertHigh > 0)
                .setAlarmHighThreshold(alertHigh)
                .setHeartRateAlarmLow(HeartRateAlarmLow.newBuilder()
                        .setAlarmLowEnabled(alertLow > 0)
                        .setAlarmLowThreshold(alertLow))
                .setUnknown7(1);

        getSupport().sendCommand(
                "set heart rate config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_HEART_RATE_SET)
                        .setHealth(Health.newBuilder().setHeartRate(heartRate))
                        .build()
        );
    }

    private void handleStandingReminderConfig(final StandingReminder standingReminder) {
        LOG.debug("Got standing reminder config");

        final String start = XiaomiPreferences.prefFromHourMin(standingReminder.getStart());
        final String end = XiaomiPreferences.prefFromHourMin(standingReminder.getEnd());
        final String dndStart = XiaomiPreferences.prefFromHourMin(standingReminder.getDndStart());
        final String dndEnd = XiaomiPreferences.prefFromHourMin(standingReminder.getDndEnd());

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_INACTIVITY, true)
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE, standingReminder.getEnabled())
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_START, start)
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_END, end)
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND, standingReminder.getDnd())
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_START, dndStart)
                .withPreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_END, dndEnd);

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void setStandingReminderConfig() {
        LOG.debug("Set standing reminder config");

        final Prefs prefs = getDevicePrefs();
        final boolean enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE, false);
        final Date start = prefs.getTimePreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_START, "06:00");
        final Date end = prefs.getTimePreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_END, "22:00");
        final boolean dnd = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND, false);
        final Date dndStart = prefs.getTimePreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_START, "12:00");
        final Date dndEnd = prefs.getTimePreference(DeviceSettingsPreferenceConst.PREF_INACTIVITY_DND_END, "14:00");

        final var standingReminder = StandingReminder.newBuilder()
                .setEnabled(enabled)
                .setStart(XiaomiPreferences.prefToHourMin(start))
                .setEnd(XiaomiPreferences.prefToHourMin(end))
                .setDnd(dnd)
                .setDndStart(XiaomiPreferences.prefToHourMin(dndStart))
                .setDndEnd(XiaomiPreferences.prefToHourMin(dndEnd))
                .build();

        getSupport().sendCommand(
                "set standing reminder config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_STANDING_REMINDER_SET)
                        .setHealth(Health.newBuilder().setStandingReminder(standingReminder))
                        .build()
        );
    }

    private void handleStressConfig(final Stress stress) {
        LOG.debug("Got stress config");

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(XiaomiPreferences.FEAT_STRESS, true)
                .withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING, stress.getAllDayTracking())
                .withPreference(DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_RELAXATION_REMINDER, stress.getRelaxReminder().getEnabled());

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void setStressConfig() {
        LOG.debug("Set stress config");

        final Prefs prefs = getDevicePrefs();
        final boolean enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING, false);
        final boolean relaxReminder = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_RELAXATION_REMINDER, false);

        final var stress = Stress.newBuilder()
                .setAllDayTracking(enabled)
                .setRelaxReminder(RelaxReminder.newBuilder().setEnabled(relaxReminder).setUnknown2(0));

        getSupport().sendCommand(
                "set stress config",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CONFIG_STRESS_SET)
                        .setHealth(Health.newBuilder().setStress(stress))
                        .build()
        );
    }

    private void handleWorkoutOpen(final WorkoutOpenWatch workoutOpenWatch) {
        LOG.debug(
                "Workout open on watch: {}, workoutStarted={}, gpsStarted={}, gpsFixAcquired={}, saa={}",
                workoutOpenWatch.getSport(),
                workoutStarted,
                gpsStarted,
                gpsFixAcquired,
                saaRawSensorActive
        );

        // SaA synthetic mode: the band is asking us to confirm a hidden workout. Reply
        // (0, 2, 2) immediately without starting GPS so the band proceeds to stream raw accel.
        if (saaRawSensorActive) {
            getSupport().sendCommand(
                    "saa raw-sensor open ack",
                    XiaomiProto.Command.newBuilder()
                            .setType(COMMAND_TYPE)
                            .setSubtype(CMD_WORKOUT_WATCH_OPEN)
                            .setHealth(Health.newBuilder().setWorkoutOpenReply(
                                    WorkoutOpenReply.newBuilder()
                                            .setCode(WORKOUT_OPEN_OK)
                                            .setSelectedVersion(WORKOUT_PROTOCOL_VERSION)
                                            .setGpsAccuracy(GPS_ACCURACY_HIGH)
                            ))
                            .build()
            );
            return;
        }


        final boolean sendGpsToBand = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND, false);
        if (!sendGpsToBand || !GBLocationService.isGpsSupportedAndEnabled()) {
            getSupport().sendCommand(
                    "send location disabled",
                    XiaomiProto.Command.newBuilder()
                            .setType(COMMAND_TYPE)
                            .setSubtype(CMD_WORKOUT_WATCH_OPEN)
                            .setHealth(Health.newBuilder().setWorkoutOpenReply(
                                    WorkoutOpenReply.newBuilder()
                                            .setCode(WORKOUT_OPEN_NO_PERMISSION)
                                            .setSelectedVersion(WORKOUT_PROTOCOL_VERSION)
                                            .setGpsAccuracy(GPS_ACCURACY_UNKNOWN)
                            ))
                            .build()
            );
            return;
        }

        if (!gpsStarted) {
            gpsStarted = true;
            gpsFixAcquired = false;
            GBLocationService.start(getSupport().getContext(), getSupport().getDevice(), GBLocationProviderType.GPS, 1000);
        }

        if (!workoutStarted) {
            // Only schedule the timeout while we are still waiting for the watch to confirm
            // workout start. Once WORKOUT_STARTED has arrived, only WORKOUT_FINISHED should
            // stop GPS - newer firmwares (Mi Band 10 HyperOS 3.2.x) stop emitting
            // WorkoutOpenWatch shortly after start, so a rescheduled timeout would fire
            // mid-workout and starve the watch of GPS updates.
            final int timeout = getDevicePrefs().getInt(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND_TIMEOUT, 5000);
            gpsTimeoutHandler.removeCallbacksAndMessages(null);
            gpsTimeoutHandler.postDelayed(() -> {
                LOG.debug("Timed out waiting for workout");
                gpsStarted = false;
                gpsFixAcquired = false;
                GBLocationService.stop(getSupport().getContext(), getSupport().getDevice());
            }, timeout);
        }
    }

    private void handleWorkoutStatus(final WorkoutStatusWatch workoutStatus) {
        LOG.debug("Got workout status: {}, sport={}", workoutStatus.getStatus(), workoutStatus.getSport());

        // Ignore the synthetic SaA workout — it must not trigger OpenTracks or
        // any GPS bookkeeping.
        if (saaRawSensorActive || workoutStatus.getSport() == SAA_SYNTHETIC_SPORT) {
            return;
        }

        final boolean startOnPhone = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_WORKOUT_START_ON_PHONE, false);

        switch (workoutStatus.getStatus()) {
            case WORKOUT_STARTED:
                workoutStarted = true;
                gpsTimeoutHandler.removeCallbacksAndMessages(null);
                if (startOnPhone) {
                    OpenTracksController.startRecording(getSupport().getContext(), sportToActivityKind(workoutStatus.getSport()));
                }
                break;
            case WORKOUT_PAUSED:
            case WORKOUT_RESUMED:
                break;
            case WORKOUT_FINISHED:
                gpsStarted = false;
                gpsFixAcquired = false;
                GBLocationService.stop(getSupport().getContext(), getSupport().getDevice());
                if (startOnPhone) {
                    OpenTracksController.stopRecording(getSupport().getContext());
                }
                break;
        }
    }

    public void onSetGpsLocation(final Location location) {
        if (!gpsFixAcquired) {
            gpsFixAcquired = true;
            getSupport().sendCommand(
                    "send gps fix",
                    XiaomiProto.Command.newBuilder()
                            .setType(COMMAND_TYPE)
                            .setSubtype(CMD_WORKOUT_WATCH_OPEN)
                            .setHealth(Health.newBuilder().setWorkoutOpenReply(
                                    WorkoutOpenReply.newBuilder()
                                            .setCode(WORKOUT_OPEN_OK)
                                            .setSelectedVersion(WORKOUT_PROTOCOL_VERSION)
                                            .setGpsAccuracy(GPS_ACCURACY_HIGH)
                            ))
                            .build()
            );
        }

        if (workoutStarted) {
            final var workoutLocation = WorkoutLocation.newBuilder()
                    .setUnknown1(2)
                    .setTimestamp((int) (location.getTime() / 1000L))
                    .setLongitude(location.getLongitude())
                    .setLatitude(location.getLatitude())
                    .setAltitude(location.getAltitude())
                    .setSpeed(location.getSpeed())
                    .setBearing(location.getBearing());

            // FIXME: Check the value for these during actual workouts, but it seems to work without them
            //if (location.hasAccuracy() && location.getAccuracy() != 100) {
            //    workoutLocation.setHorizontalAccuracy(location.getAccuracy());
            //}
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy() && location.getVerticalAccuracyMeters() != 100) {
            //    workoutLocation.setVerticalAccuracy(location.getVerticalAccuracyMeters());
            //}

            getSupport().sendCommand(
                    "send gps location",
                    XiaomiProto.Command.newBuilder()
                            .setType(COMMAND_TYPE)
                            .setSubtype(CMD_WORKOUT_LOCATION)
                            .setHealth(Health.newBuilder().setWorkoutLocation(workoutLocation))
                            .build()
            );
        }
    }

    private ActivityKind sportToActivityKind(final int sport) {
        switch (sport) {
            case 1: // outdoor run
            case 5: // trail run
                return ActivityKind.RUNNING;
            case 2:
                return ActivityKind.WALKING;
            case 3: // hiking
            case 4: // trekking
                return ActivityKind.HIKING;
            case 6:
                return ActivityKind.CYCLING;
        }

        LOG.warn("Unknown sport {}", sport);

        return ActivityKind.UNKNOWN;
    }

    public XiaomiActivityFileFetcher getActivityFetcher() {
        return activityFetcher;
    }

    public void onFetchRecordedData(final int dataTypes) {
        LOG.debug("Fetch recorded data: {}", String.format("0x%08X", dataTypes));

        fetchRecordedDataToday();
    }

    private void fetchRecordedDataToday() {
        getSupport().sendCommand(
                "fetch recorded data today",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_ACTIVITY_FETCH_TODAY)
                        .setHealth(Health.newBuilder().setActivitySyncRequestToday(
                                // TODO official app sends 0, but sometimes 1?
                                ActivitySyncRequestToday.newBuilder().setUnknown1(0)
                        ))
                        .build()
        );
    }

    private void fetchRecordedDataPast() {
        getSupport().sendCommand(
                "fetch recorded data past",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_ACTIVITY_FETCH_PAST)
                        .build()
        );
    }

    public void requestRecordedData(final XiaomiActivityFileId fileId) {
        getSupport().sendCommand(
                "request recorded data",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_ACTIVITY_FETCH_REQUEST)
                        .setHealth(Health.newBuilder().setActivityRequestFileIds(
                                ByteString.copyFrom(fileId.toBytes())
                        ))
                        .build()
        );
    }

    public void ackRecordedData(final XiaomiActivityFileId fileId) {
        getSupport().sendCommand(
                "ack recorded data",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_ACTIVITY_FETCH_ACK)
                        .setHealth(Health.newBuilder().setActivitySyncAckFileIds(
                                ByteString.copyFrom(fileId.toBytes())
                        ))
                        .build()
        );
    }

    public void handleActivityFetchResponse(final int subtype, final byte[] recordIds) {
        if ((recordIds.length % 7) != 0) {
            LOG.warn("recordIds {} length = {}, not a multiple of 7, can't parse", GB.hexdump(recordIds), recordIds.length);
            return;
        }

        LOG.debug("Got {} activity file IDs", recordIds.length / 7);

        final ByteBuffer buf = ByteBuffer.wrap(recordIds).order(ByteOrder.LITTLE_ENDIAN);
        final List<XiaomiActivityFileId> fileIds = new ArrayList<>();

        while (buf.position() < buf.limit()) {
            final XiaomiActivityFileId fileId = XiaomiActivityFileId.from(buf);
            LOG.debug("Got activity to fetch: {}", fileId);
            if (fileId.getTimestamp().getTime() == 0 && fileId.getVersion() == 0) {
                LOG.warn("Skipping invalid file with no timestamp and version");
                continue;
            }
            fileIds.add(fileId);
        }
        if (subtype == CMD_ACTIVITY_FETCH_TODAY) {
            activityFetcher.setAwaitingPastResponse(true);
        }

        activityFetcher.fetch(fileIds);

        if (subtype == CMD_ACTIVITY_FETCH_TODAY) {
            LOG.debug("Fetch recorded data from the past");
            fetchRecordedDataPast();
        } else if (subtype == CMD_ACTIVITY_FETCH_PAST) {
            activityFetcher.setAwaitingPastResponse(false);
            activityFetcher.resumeFetching();
        }
    }

    public void onHeartRateTest() {
        LOG.debug("Trigger heart rate one-shot test");

        setRealtimeConsumer(RealtimeConsumer.ONE_SHOT, true);
    }

    public void enableRealtimeStats(final boolean enable) {
        setRealtimeConsumer(RealtimeConsumer.UI, enable);
    }

    private void setRealtimeConsumer(final RealtimeConsumer consumer, final boolean enable) {
        LOG.debug("Realtime stats consumer {}: {}", consumer, enable);

        final boolean streaming;
        synchronized (realtimeConsumers) {
            final boolean wasStreaming = !realtimeConsumers.isEmpty();
            final boolean changed = enable ? realtimeConsumers.add(consumer) : realtimeConsumers.remove(consumer);
            streaming = !realtimeConsumers.isEmpty();
            if (!changed || wasStreaming == streaming) {
                return;
            }
        }

        previousSteps = -1;
        sendRealtimeStats(streaming);
    }

    private void sendRealtimeStats(final boolean enable) {
        getSupport().sendCommand(
                "realtime data",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(enable ? CMD_REALTIME_STATS_START : CMD_REALTIME_STATS_STOP)
                        .build()
        );
    }

    private void handleRealtimeStats(final RealTimeStats realTimeStats) {
        LOG.debug("Got realtime stats");

        final boolean noConsumers;
        final boolean oneShot;
        synchronized (realtimeConsumers) {
            noConsumers = realtimeConsumers.isEmpty();
            oneShot = realtimeConsumers.contains(RealtimeConsumer.ONE_SHOT);
        }

        if (noConsumers) {
            // Failsafe in case it gets out of sync, stop it
            sendRealtimeStats(false);
            return;
        }

        if (oneShot) {
            if (realTimeStats.getHeartRate() <= 10) {
                return;
            }
            setRealtimeConsumer(RealtimeConsumer.ONE_SHOT, false);
        }

        if (previousSteps == -1) {
            previousSteps = realTimeStats.getSteps();
        }

        final XiaomiActivitySample sample;
        try (final DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();

            final GBDevice gbDevice = getSupport().getDevice();
            final Device device = DBHelper.getDevice(gbDevice, session);
            final User user = DBHelper.getUser(session);
            final int ts = (int) (System.currentTimeMillis() / 1000);
            final XiaomiSampleProvider provider = new XiaomiSampleProvider(gbDevice, session);
            sample = provider.createActivitySample();

            sample.setDeviceId(device.getId());
            sample.setUserId(user.getId());
            sample.setTimestamp(ts);
            sample.setHeartRate(realTimeStats.getHeartRate());
            sample.setSteps(realTimeStats.getSteps() - previousSteps);
            sample.setRawKind(ActivityKind.UNKNOWN.getCode());
            sample.setRawIntensity(ActivitySample.NOT_MEASURED);
        } catch (final Exception e) {
            LOG.error("Error creating activity sample", e);
            return;
        }

        previousSteps = realTimeStats.getSteps();

        final Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES)
                .putExtra(GBDevice.EXTRA_DEVICE, getSupport().getDevice())
                .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample);
        LocalBroadcastManager.getInstance(getSupport().getContext()).sendBroadcast(intent);

        if (realTimeStats.getHeartRate() > 0) {
            lastHeartRate = realTimeStats.getHeartRate();
        }

        if (sleepAsAndroidSender != null && realTimeStats.getHeartRate() > 0) {
            sleepAsAndroidSender.onHrChanged(realTimeStats.getHeartRate(), 0);
        }
    }

    /**
     * Start a SaA synthetic workout on the band. Sequence:
     *  1. REALTIME_STATS_START -- enables HR/steps stream (existing path)
     *  2. WORKOUT_WATCH_STATUS(status=STARTED, sport=SAA_SYNTHETIC_SPORT) -- tells the band to
     *     open a hidden workout. Band then sends WORKOUT_WATCH_OPEN to us; handleWorkoutOpen
     *     replies (0, 2, 2) when saaRawSensorActive is true and the band starts streaming
     *     subtype-53 raw accel batches.
     */
    public void startRawSensor() {
        saaRawSensorActive = true;
        saaWorkoutStartedMs = SystemClock.elapsedRealtime();
        saaStatsActiveUntilMs = saaWorkoutStartedMs + WORKOUT_STATS_ACTIVE_WINDOW_MS;
        lastHeartRate = HEART_RATE_UNKNOWN;

        setRealtimeConsumer(RealtimeConsumer.SLEEP_AS_ANDROID, true);
        sendWorkoutStatus(WORKOUT_STARTED);
        startWorkoutStatsTicker();
    }

    /**
     * Tear down the SaA synthetic workout. Sequence:
     *  1. REALTIME_STATS_STOP
     *  2. WORKOUT_WATCH_STATUS(status=PAUSED, ...)
     *  3. WORKOUT_WATCH_STATUS(status=FINISHED, ...) -- final close
     */
    public void stopRawSensor() {
        if (!saaRawSensorActive) return;

        stopWorkoutStatsTicker();
        setRealtimeConsumer(RealtimeConsumer.SLEEP_AS_ANDROID, false);
        sendWorkoutStatus(WORKOUT_PAUSED);
        sendWorkoutStatus(WORKOUT_FINISHED);
        saaRawSensorActive = false;
    }

    private void startWorkoutStatsTicker() {
        saaWorkoutStatsHandler.removeCallbacksAndMessages(null);
        saaWorkoutStatsHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!saaRawSensorActive) {
                    return;
                }
                sendWorkoutStats();
                saaWorkoutStatsHandler.postDelayed(this, workoutStatsIntervalMs());
            }
        });
    }

    private long workoutStatsIntervalMs() {
        return SystemClock.elapsedRealtime() < saaStatsActiveUntilMs
                ? WORKOUT_STATS_INTERVAL_MS
                : WORKOUT_STATS_IDLE_INTERVAL_MS;
    }

    private void stopWorkoutStatsTicker() {
        saaWorkoutStatsHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Push the values the band shows on its workout screen. Only elapsed time and heart rate are
     * meaningful for the SaA synthetic workout; the rest stay at zero so the band does not display
     * figures that were never measured.
     */
    private void sendWorkoutStats() {
        final int elapsedSeconds = (int) ((SystemClock.elapsedRealtime() - saaWorkoutStartedMs) / 1000);

        getSupport().sendCommand(
                "saa workout stats",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_WORKOUT_STATS_PHONE)
                        .setHealth(Health.newBuilder().setWorkoutStatsPhone(
                                WorkoutStatsPhone.newBuilder()
                                        .setDurationSeconds(Math.max(0, elapsedSeconds))
                                        .setHeartRate(lastHeartRate)
                                        .setCalories(0)
                                        .setDistance(0)
                        ))
                        .build()
        );
    }

    private void sendWorkoutStatus(final int status) {
        final long now = System.currentTimeMillis();
        final int ts = (int) (now / 1000);
        final int tzOffsetQuarterHours = TimeZone.getDefault().getOffset(now) / 60000 / 15;
        getSupport().sendCommand(
                "saa workout status " + status,
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_WORKOUT_WATCH_STATUS)
                        .setHealth(Health.newBuilder().setWorkoutStatusWatch(
                                WorkoutStatusWatch.newBuilder()
                                        .setTimestamp(ts)
                                        .setSportInfo(WorkoutStatusWatchSport.newBuilder()
                                                .setTzOffsetQuarterHours(tzOffsetQuarterHours))
                                        .setSport(SAA_SYNTHETIC_SPORT)
                                        .setStatus(status)
                                        .setSupportedVersions(3)
                        ))
                        .build()
        );
    }

    /**
     * The stats the watch computes for itself while a workout is open, which it pushes whether the
     * watch or the phone started that workout. Its calorie count is its own: it keeps climbing at a
     * rate the sport type fixes, whatever the heart rate says and whatever the phone reports.
     */
    private void handleWorkoutStatsWatch(final WorkoutStatsWatch stats) {
        LOG.debug("Got workout stats from watch: hr={} calories={} steps={} distance={}",
                stats.getHeartRate(), stats.getCalories(), stats.getSteps(), stats.getDistance());

        if (stats.getHeartRate() <= 0) {
            return;
        }

        lastHeartRate = stats.getHeartRate();

        if (saaRawSensorActive && sleepAsAndroidSender != null) {
            sleepAsAndroidSender.onHrChanged(stats.getHeartRate(), 0);
        }
    }

    private void handleRawSensorBatch(final RawSensorBatch batch) {
        final int n = batch.getAccelCount();
        LOG.debug("Got raw sensor batch: {} accel samples", n);
        if (sleepAsAndroidSender != null && n > 0) {
            for (int i = 0; i < n; i++) {
                final AxisSensor s = batch.getAccel(i);
                sleepAsAndroidSender.onAccelChanged(s.getX(), s.getY(), s.getZ());
            }
        }

    }
}
