/*  Copyright (C) 2015-2026 Alberto, Andreas Böhler, Andreas Shimokawa,
    Arjan Schrijver, Carsten Pfeiffer, criogenic, Daniel Dakhno, Daniele Gobbetti,
    Davis Mosenkovs, Frank Slezak, Gabriele Monaco, Gordon Williams, ivanovlev,
    José Rebelo, Julien Pivotto, Kasha, mvn23, Petr Vaněk, Roi Greenberg,
    Sebastian Kranz, Steffen Liebergeld, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.impl;

import static nodomain.freeyourgadget.gadgetbridge.util.JavaExtensions.coalesce;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executor;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Contact;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationRouteSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Reminder;
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceCommunicationService;
import nodomain.freeyourgadget.gadgetbridge.util.RtlUtils;

/**
 * Fires an intent with an action to be called on the DeviceSupport class, to be handled by DeviceCommunicationService.
 * The intents created here must be handled in {@link nodomain.freeyourgadget.gadgetbridge.service.DeviceActionHandler}
 * accordingly.
 */
public class GBDeviceService implements DeviceService {
    protected final Context mContext;
    private final GBDevice mDevice;
    private final Class<? extends Service> mServiceClass;
    private final Executor mainExecutor = ContextCompat.getMainExecutor(GBApplication.getContext());

    private final ConflatingDispatcher<NavigationInfoSpec> navigationDispatcher =
            new ConflatingDispatcher<>(mainExecutor, this::forwardNavigationInfo);
    private static final Logger LOG = LoggerFactory.getLogger(GBDeviceService.class);

    public GBDeviceService(@NonNull Context context) {
        this(context, null);
    }

    public GBDeviceService(@NonNull Context context, GBDevice device) {
        mContext = context;
        mDevice = device;
        mServiceClass = DeviceCommunicationService.class;
    }

    @Override
    public DeviceService forDevice(final GBDevice device) {
        return new GBDeviceService(mContext, device);
    }

    @Nullable
    @Override
    public GBDevice getDevice() {
        return mDevice;
    }

    protected Intent createIntent() {
        return new Intent(mContext, mServiceClass);
    }

    protected void invokeService(@NonNull Intent intent) {

        if (mDevice != null) {
            intent.putExtra(GBDevice.EXTRA_DEVICE, mDevice);
        }
        try {
            mContext.startService(intent);
        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException during startService ({})", intent.getAction(), e);
        }
    }

    protected void stopService(Intent intent) {
        mContext.stopService(intent);
    }

    @Override
    public void connect() {
        connect(false);
    }

    @Override
    public void connect(boolean firstTime) {
        Intent intent = createIntent().setAction(ACTION_CONNECT)
                .putExtra(EXTRA_CONNECT_FIRST_TIME, firstTime);
        invokeService(intent);
    }

    @Override
    public void disconnect() {
        Intent intent = createIntent().setAction(ACTION_DISCONNECT);
        invokeService(intent);
    }

    @Override
    public void quit() {
        Intent intent = createIntent();
        stopService(intent);
    }

    @Override
    public void requestDeviceInfo() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_DEVICEINFO);
        invokeService(intent);
    }

    @Override
    public void onNotification(@NonNull NotificationSpec notificationSpec) {
        String messagePrivacyMode = GBApplication.getPrefs().getString("pref_message_privacy_mode",
                GBApplication.getContext().getString(R.string.p_message_privacy_mode_off));
        boolean hideMessageDetails = messagePrivacyMode.equals(GBApplication.getContext().getString(R.string.p_message_privacy_mode_complete));
        boolean hideMessageBodyOnly = messagePrivacyMode.equals(GBApplication.getContext().getString(R.string.p_message_privacy_mode_bodyonly));

        NotificationSpec privacyCleared = notificationSpec.copyOf();

        privacyCleared = privacyCleared.withSender(coalesce(privacyCleared.getSender(), getContactDisplayNameByNumber(privacyCleared.getPhoneNumber())));

        if (hideMessageDetails) {
            privacyCleared = privacyCleared.withMessageDetailsCleared();
        } else if (hideMessageBodyOnly) {
            privacyCleared = privacyCleared.withMessageBodyCleared();
        }

        Intent intent = createIntent().setAction(ACTION_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_SPEC, privacyCleared.withRtlFix());
        invokeService(intent);
    }

    @Override
    public void onDeleteNotification(int id) {
        Intent intent = createIntent().setAction(ACTION_DELETE_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_ID, id);
        invokeService(intent);

    }

    @Override
    public void onSetTime() {
        Intent intent = createIntent().setAction(ACTION_SETTIME);
        invokeService(intent);
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        Intent intent = createIntent().setAction(ACTION_SET_ALARMS)
                .putExtra(EXTRA_ALARMS, alarms);
        invokeService(intent);
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        Context context = GBApplication.getContext();
        String currentPrivacyMode = GBApplication.getPrefs().getString("pref_call_privacy_mode", GBApplication.getContext().getString(R.string.p_call_privacy_mode_off));
        if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_name))) {
            callSpec.setName(callSpec.getNumber());
        } else if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_complete))) {
            callSpec.setNumber(null);
            callSpec.setName(null);
        } else if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_number))) {
            callSpec.setName(coalesce(callSpec.getName(), getContactDisplayNameByNumber(callSpec.getNumber())));
            if (callSpec.getName() != null && !callSpec.getName().equals(callSpec.getNumber())) {
                callSpec.setNumber(null);
            }
        } else {
            callSpec.setName(coalesce(callSpec.getName(), getContactDisplayNameByNumber(callSpec.getNumber())));
        }

        Intent intent = createIntent().setAction(ACTION_CALLSTATE)
                .putExtra(EXTRA_CALL_SPEC, callSpec.withRtlFix());

        invokeService(intent);
    }

    @Override
    public void onSetCannedMessages(@NonNull CannedMessagesSpec cannedMessagesSpec) {
        Intent intent = createIntent().setAction(ACTION_SETCANNEDMESSAGES)
                .putExtra(EXTRA_CANNEDMESSAGES_SPEC, cannedMessagesSpec);
        invokeService(intent);
    }

    @Override
    public void onSetMusicState(@NonNull MusicStateSpec stateSpec) {
        Intent intent = createIntent().setAction(ACTION_SETMUSICSTATE)
                .putExtra(EXTRA_MUSIC_STATE_SPEC, stateSpec);
        invokeService(intent);
    }

    @Override
    public void onSetPhoneVolume(final float volume) {
        Intent intent = createIntent().setAction(ACTION_SET_PHONE_VOLUME)
                .putExtra(EXTRA_PHONE_VOLUME, volume);
        invokeService(intent);
    }

    @Override
    public void onChangePhoneSilentMode(int ringerMode) {
        Intent intent = createIntent().setAction(ACTION_SET_PHONE_SILENT_MODE)
                .putExtra(EXTRA_PHONE_RINGER_MODE, ringerMode);
        invokeService(intent);
    }

    @Override
    public void onSetReminders(ArrayList<? extends Reminder> reminders) {
        Intent intent = createIntent().setAction(ACTION_SET_REMINDERS)
                .putExtra(EXTRA_REMINDERS, reminders);
        invokeService(intent);
    }

    @Override
    public void onSetLoyaltyCards(final ArrayList<LoyaltyCard> cards) {
        final Intent intent = createIntent().setAction(ACTION_SET_LOYALTY_CARDS)
                .putExtra(EXTRA_LOYALTY_CARDS, cards);
        invokeService(intent);
    }

    @Override
    public void onSetWorldClocks(ArrayList<? extends WorldClock> clocks) {
        Intent intent = createIntent().setAction(ACTION_SET_WORLD_CLOCKS)
                .putExtra(EXTRA_WORLD_CLOCKS, clocks);
        invokeService(intent);
    }

    @Override
    public void onSetContacts(ArrayList<? extends Contact> contacts) {
        Intent intent = createIntent().setAction(ACTION_SET_CONTACTS)
                .putExtra(EXTRA_CONTACTS, contacts);
        invokeService(intent);
    }

    @Override
    public void onSyncWorkoutTemplate(final long templateId) {
        Intent intent = createIntent().setAction(ACTION_SYNC_WORKOUT_TEMPLATE)
                .putExtra(EXTRA_WORKOUT_TEMPLATE_ID, templateId);
        invokeService(intent);
    }

    @Override
    public void onSetMusicInfo(@NonNull MusicSpec musicSpec) {
        final MusicSpec withRtlFix = musicSpec.withRtlFix();
        Intent intent = createIntent().setAction(ACTION_SETMUSICINFO)
                .putExtra(EXTRA_MUSIC_SPEC, withRtlFix);
        invokeService(intent);
    }

    @Override
    public void onSetNavigationInfo(@NonNull NavigationInfoSpec navigationInfoSpec) {
        navigationDispatcher.offer(navigationInfoSpec);
    }

    @Override
    public void onSetNavigationRoute(@NonNull NavigationRouteSpec navigationRouteSpec) {
        Intent intent = createIntent().setAction(ACTION_SETNAVIGATIONROUTE)
                .putExtra(EXTRA_NAVIGATION_ROUTE_SPEC, navigationRouteSpec);
        invokeService(intent);
    }

    private void forwardNavigationInfo(NavigationInfoSpec navigationInfoSpec) {
        Intent intent = createIntent().setAction(ACTION_SETNAVIGATIONINFO)
                .putExtra(EXTRA_NAVIGATION_INSTRUCTION, navigationInfoSpec.getInstruction())
                .putExtra(EXTRA_NAVIGATION_NEXT_ACTION, navigationInfoSpec.getNextAction())
                .putExtra(EXTRA_NAVIGATION_DISTANCE_TO_TURN, navigationInfoSpec.getDistanceToTurn())
                .putExtra(EXTRA_NAVIGATION_DISTANCE_TO_TARGET, navigationInfoSpec.getDistanceToTarget())
                .putExtra(EXTRA_NAVIGATION_COMPLETION_PERCENT, navigationInfoSpec.getCompletionPercent());
        if(navigationInfoSpec.getTotalTimeToDestination() != null) {
            intent.putExtra(EXTRA_NAVIGATION_TIME_TO_DESTINATION, navigationInfoSpec.getTotalTimeToDestination());
        } else {
            intent.putExtra(EXTRA_NAVIGATION_ETA, navigationInfoSpec.getETA());
        }
        invokeService(intent);
    }

    @Override
    public void onInstallApp(Uri uri, @NonNull final Bundle options) {
        Intent intent = createIntent().setAction(ACTION_INSTALL)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_OPTIONS, options);
        invokeService(intent);
    }

    @Override
    public void onAppInfoReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_APPINFO);
        invokeService(intent);
    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {
        Intent intent = createIntent().setAction(ACTION_STARTAPP)
                .putExtra(EXTRA_APP_UUID, uuid)
                .putExtra(EXTRA_APP_START, start);
        invokeService(intent);
    }

    @Override
    public void onAppDownload(UUID uuid) {
        Intent intent = createIntent().setAction(ACTION_DOWNLOADAPP)
                .putExtra(EXTRA_APP_UUID, uuid);
        invokeService(intent);
    }

    @Override
    public void onAppDelete(UUID uuid) {
        Intent intent = createIntent().setAction(ACTION_DELETEAPP)
                .putExtra(EXTRA_APP_UUID, uuid);
        invokeService(intent);
    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {
        Intent intent = createIntent().setAction(ACTION_APP_CONFIGURE)
                .putExtra(EXTRA_APP_UUID, appUuid)
                .putExtra(EXTRA_APP_CONFIG, config);

        if (id != null) {
            intent.putExtra(EXTRA_APP_CONFIG_ID, id);
        }
        invokeService(intent);
    }

    @Override
    public void onAppConfigRequest(final UUID uuid) {
        Intent intent = createIntent().setAction(ACTION_APP_CONFIG_REQUEST)
                .putExtra(EXTRA_APP_UUID, uuid);

        invokeService(intent);
    }

    @Override
    public void onAppConfigSet(final UUID uuid, final ArrayList<DynamicAppConfig> configs) {
        Intent intent = createIntent().setAction(ACTION_APP_CONFIG_SET)
                .putExtra(EXTRA_APP_UUID, uuid)
                .putParcelableArrayListExtra(EXTRA_APP_CONFIG, configs);

        invokeService(intent);
    }

    @Override
    public void onAppReorder(UUID[] uuids) {
        Intent intent = createIntent().setAction(ACTION_APP_REORDER)
                .putExtra(EXTRA_APP_UUID, uuids);
        invokeService(intent);
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        Intent intent = createIntent().setAction(ACTION_FETCH_RECORDED_DATA)
                .putExtra(EXTRA_RECORDED_DATA_TYPES, dataTypes);
        invokeService(intent);
    }

    @Override
    public void onReboot() {
        Intent intent = createIntent().setAction(ACTION_REBOOT);
        invokeService(intent);
    }

    @Override
    public void onFactoryReset() {
        Intent intent = createIntent().setAction(ACTION_FACTORY_RESET);
        invokeService(intent);
    }

    @Override
    public void onHeartRateTest() {
        Intent intent = createIntent().setAction(ACTION_HEARTRATE_TEST);
        invokeService(intent);
    }

    @Override
    public void onFindDevice(boolean start) {
        onFindDevice(start, FindDeviceTarget.ALL);
    }

    @Override
    public void onFindDevice(final boolean start, @NonNull final FindDeviceTarget target) {
        Intent intent = createIntent().setAction(ACTION_FIND_DEVICE)
                .putExtra(EXTRA_FIND_START, start)
                .putExtra(EXTRA_FIND_TARGET, target);
        invokeService(intent);
    }

    @Override
    public void onFindPhone(final boolean start) {
        Intent intent = createIntent().setAction(ACTION_PHONE_FOUND)
                .putExtra(EXTRA_FIND_START, start);
        invokeService(intent);
    }

    @Override
    public void onSetConstantVibration(int intensity) {
        Intent intent = createIntent().setAction(ACTION_SET_CONSTANT_VIBRATION)
                .putExtra(EXTRA_VIBRATION_INTENSITY, intensity);
        invokeService(intent);
    }

    @Override
    public void onScreenshotReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_SCREENSHOT);
        invokeService(intent);
    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_REALTIME_STEPS)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {
        Intent intent = createIntent().setAction(ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL)
                .putExtra(EXTRA_INTERVAL_SECONDS, seconds);
        invokeService(intent);
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onAddCalendarEvent(@NonNull CalendarEventSpec calendarEventSpec) {
        final CalendarEventSpec withRtlFix = calendarEventSpec.withRtlFix();

        Intent intent = createIntent().setAction(ACTION_ADD_CALENDAREVENT)
                .putExtra(EXTRA_CALENDAREVENT_SPEC, withRtlFix);
        invokeService(intent);
    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {
        Intent intent = createIntent().setAction(ACTION_DELETE_CALENDAREVENT)
                .putExtra(EXTRA_CALENDAREVENT_TYPE, type)
                // TODO: If swapping to EVENT_ID, change this here.
                .putExtra(EXTRA_CALENDAREVENT_ID, id);
        invokeService(intent);
    }

    @Override
    public void onSendConfiguration(@NonNull String config) {
        Intent intent = createIntent().setAction(ACTION_SEND_CONFIGURATION)
                .putExtra(EXTRA_CONFIG, config);
        invokeService(intent);
    }

    @Override
    public void onReadConfiguration(String config) {
        Intent intent = createIntent().setAction(ACTION_READ_CONFIGURATION)
                .putExtra(EXTRA_CONFIG, config);
        invokeService(intent);
    }

    @Override
    public void onTestNewFunction(@Nullable Bundle options) {
        Intent intent = createIntent().setAction(ACTION_TEST_NEW_FUNCTION);
        intent.putExtra(EXTRA_OPTIONS, options);
        invokeService(intent);
    }

    @Override
    public void onSendWeather() {
        Intent intent = createIntent().setAction(ACTION_SEND_WEATHER);
        invokeService(intent);
    }

    /**
     * Returns contact DisplayName by call number
     *
     * @param number contact number
     * @return contact DisplayName, if found it
     */
    private String getContactDisplayNameByNumber(String number) {
        if (number == null || number.isEmpty()) {
            return number;
        }

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(number));

        String name = number;

        try (Cursor contactLookup = mContext.getContentResolver().query(uri, null, null, null, null)) {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                int index = contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                if (index >= 0) {
                    name = contactLookup.getString(index);
                }
            }
        } catch (SecurityException e) {
            // ignore, just return name below
        }

        return name;
    }

    @Override
    public void onSetFmFrequency(float frequency) {
        Intent intent = createIntent().setAction(ACTION_SET_FM_FREQUENCY)
                .putExtra(EXTRA_FM_FREQUENCY, frequency);
        invokeService(intent);
    }

    @Override
    public void onSetLedColor(int color) {
        Intent intent = createIntent().setAction(ACTION_SET_LED_COLOR)
                .putExtra(EXTRA_LED_COLOR, color);
        invokeService(intent);
    }

    @Override
    public void onPowerOff() {
        Intent intent = createIntent().setAction(ACTION_POWER_OFF);
        invokeService(intent);
    }

    @Override
    public void onSetGpsLocation(Location location) {
        Intent intent = createIntent().setAction(ACTION_SET_GPS_LOCATION);
        intent.putExtra(EXTRA_GPS_LOCATION, location);
        invokeService(intent);
    }

    @Override
    public void onSleepAsAndroidAction(String action, Bundle extras) {
        Intent intent = createIntent().setAction(ACTION_SLEEP_AS_ANDROID);
        intent.putExtra(EXTRA_SLEEP_AS_ANDROID_ACTION, action);
        if (extras != null) {
            intent.putExtras(extras);
        }
        invokeService(intent);
    }

    @Override
    public void onCameraStatusChange(GBDeviceEventCameraRemote.Event event, String filename) {
        Intent intent = createIntent().setAction(ACTION_CAMERA_STATUS_CHANGE);
        intent.putExtra(EXTRA_CAMERA_EVENT, GBDeviceEventCameraRemote.eventToInt(event));
        if (event == GBDeviceEventCameraRemote.Event.TAKE_PICTURE)
            intent.putExtra(EXTRA_CAMERA_FILENAME, filename);
        invokeService(intent);
    }

    @Override
    public void onMusicListReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_MUSIC_LIST);
        invokeService(intent);
    }

    @Override
    public void onMusicOperation(int operation, int playlistIndex, String playlistName, ArrayList<Integer> musicIds) {
        Intent intent = createIntent().setAction(ACTION_REQUEST_MUSIC_OPERATION);
        intent.putExtra(EXTRA_REQUEST_MUSIC_OPERATION, operation);
        intent.putExtra(EXTRA_REQUEST_MUSIC_PLAY_LIST_INDEX, playlistIndex);
        intent.putExtra(EXTRA_REQUEST_MUSIC_PLAY_LIST_NAME, playlistName);
        intent.putExtra(EXTRA_REQUEST_MUSIC_MUSIC_IDS, musicIds);
        invokeService(intent);
    }
}
