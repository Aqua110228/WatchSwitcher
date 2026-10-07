package nodomain.freeyourgadget.gadgetbridge.service;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Contact;
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationRouteSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.model.Reminder;
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.util.language.LanguageUtils;
import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.*;

/**
 * Translates the Intent sent by {GBDeviceService} and calls the corresponding method in the device support class.
 */
@SuppressWarnings({"unchecked", "deprecation"})
public class DeviceActionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceActionHandler.class);

    private DeviceActionHandler() {
        // utility class
    }

    public static void handle(final GBDevice device,
                              final DeviceSupport deviceSupport,
                              final Context context,
                              final Intent intent,
                              final String action) {
        final DevicePrefs devicePrefs = GBApplication.getDevicePrefs(device);

        final Transliterator transliterator = LanguageUtils.getTransliterator(device);

        // Copy the incoming intent to make sure we don't modify it before it gets passed to other devices
        final Intent intentCopy = (Intent) intent.clone();

        switch (action) {
            case ACTION_REQUEST_DEVICEINFO:
                device.sendDeviceUpdateIntent(context, GBDevice.DeviceUpdateSubject.NOTHING);
                break;
            case ACTION_NOTIFICATION: {
                final NotificationSpec notificationSpec = intentCopy.getParcelableExtra(EXTRA_NOTIFICATION_SPEC);
                if (notificationSpec != null) {
                //TODO: check if at least one of the attached actions is a reply action instead?
                if ((notificationSpec.getAttachedActions() != null && !notificationSpec.getAttachedActions().isEmpty())
                        || (notificationSpec.getType() == NotificationType.GENERIC_SMS && notificationSpec.getPhoneNumber() != null)) {
                    // NOTE: maybe not where it belongs
                    // I would rather like to save that as an array in SharedPreferences
                    // this would work, but I don't know how to do the same in the Settings Activity's xXML
                    ArrayList<String> replies = new ArrayList<>();
                    for (int i = 1; i <= 16; i++) {
                        String reply = devicePrefs.getString("canned_reply_" + i, null);
                        if (reply != null && !reply.isEmpty()) {
                            replies.add(reply);
                        }
                    }
                    notificationSpec.setCannedReplies(replies.toArray(new String[0]));
                }
                    deviceSupport.onNotification(notificationSpec.transliterated(deviceSupport, transliterator));
                } else {
                    LOG.warn("Received a null ParcelableExtra, expected a NotificationSpec.");
                }
                break;
            }
            case ACTION_DELETE_NOTIFICATION: {
                deviceSupport.onDeleteNotification(intentCopy.getIntExtra(EXTRA_NOTIFICATION_ID, -1));
                break;
            }
            case ACTION_ADD_CALENDAREVENT: {
                final CalendarEventSpec calendarEventSpec = intentCopy.getParcelableExtra(EXTRA_CALENDAREVENT_SPEC);
                if (calendarEventSpec != null) {
                    deviceSupport.onAddCalendarEvent(calendarEventSpec.transliterated(deviceSupport, transliterator));
                }
                break;
            }
            case ACTION_DELETE_CALENDAREVENT: {
                final long id = intentCopy.getLongExtra(EXTRA_CALENDAREVENT_ID, -1);
                final byte type = intentCopy.getByteExtra(EXTRA_CALENDAREVENT_TYPE, (byte) -1);
                deviceSupport.onDeleteCalendarEvent(type, id);
                break;
            }
            case ACTION_REBOOT: {
                deviceSupport.onReboot();
                break;
            }
            case ACTION_FACTORY_RESET: {
                deviceSupport.onFactoryReset();
                break;
            }
            case ACTION_HEARTRATE_TEST: {
                deviceSupport.onHeartRateTest();
                break;
            }
            case ACTION_FETCH_RECORDED_DATA: {
                final int dataTypes = intentCopy.getIntExtra(EXTRA_RECORDED_DATA_TYPES, 0);
                deviceSupport.onFetchRecordedData(dataTypes);
                break;
            }
            case ACTION_FIND_DEVICE: {
                final boolean start = intentCopy.getBooleanExtra(EXTRA_FIND_START, false);
                final FindDeviceTarget target = (FindDeviceTarget) intentCopy.getSerializableExtra(EXTRA_FIND_TARGET);
                deviceSupport.onFindDevice(start, target != null ? target : FindDeviceTarget.ALL);
                break;
            }
            case ACTION_PHONE_FOUND: {
                final boolean start = intentCopy.getBooleanExtra(EXTRA_FIND_START, false);
                deviceSupport.onFindPhone(start);
                break;
            }
            case ACTION_SET_CONSTANT_VIBRATION: {
                final int intensity = intentCopy.getIntExtra(EXTRA_VIBRATION_INTENSITY, 0);
                deviceSupport.onSetConstantVibration(intensity);
                break;
            }
            case ACTION_CALLSTATE:
                final CallSpec callSpec = intentCopy.getParcelableExtra(EXTRA_CALL_SPEC);
                if (callSpec != null) {
                    deviceSupport.onSetCallState(callSpec.transliterated(deviceSupport, transliterator));
                } else {
                    deviceSupport.onSetCallState(callSpec);
                }
                break;
            case ACTION_SETCANNEDMESSAGES:
                final CannedMessagesSpec cannedMessagesSpec = intentCopy.getParcelableExtra(EXTRA_CANNEDMESSAGES_SPEC);
                if(cannedMessagesSpec != null)
                    deviceSupport.onSetCannedMessages(cannedMessagesSpec);
                break;
            case ACTION_SETTIME:
                deviceSupport.onSetTime();
                break;
            case ACTION_SETMUSICINFO:
                final MusicSpec musicSpec = intentCopy.getParcelableExtra(EXTRA_MUSIC_SPEC);
                if (musicSpec != null) {
                    deviceSupport.onSetMusicInfo(musicSpec.transliterated(deviceSupport, transliterator));
                }
                break;
            case ACTION_SET_PHONE_VOLUME:
                final float phoneVolume = intentCopy.getFloatExtra(EXTRA_PHONE_VOLUME, 0);
                deviceSupport.onSetPhoneVolume(phoneVolume);
                break;
            case ACTION_SET_PHONE_SILENT_MODE:
                final int ringerMode = intentCopy.getIntExtra(EXTRA_PHONE_RINGER_MODE, -1);
                deviceSupport.onChangePhoneSilentMode(ringerMode);
                break;
            case ACTION_SETMUSICSTATE:
                final MusicStateSpec stateSpec = intentCopy.getParcelableExtra(EXTRA_MUSIC_STATE_SPEC);
                if (stateSpec != null)
                    deviceSupport.onSetMusicState(stateSpec);
                break;
            case ACTION_SETNAVIGATIONINFO:
                final NavigationInfoSpec navigationInfoSpec = new NavigationInfoSpec();
                navigationInfoSpec.setInstruction(intentCopy.getStringExtra(EXTRA_NAVIGATION_INSTRUCTION));
                navigationInfoSpec.setNextAction(intentCopy.getIntExtra(EXTRA_NAVIGATION_NEXT_ACTION, 0));
                navigationInfoSpec.setDistanceToTurn(intentCopy.getStringExtra(EXTRA_NAVIGATION_DISTANCE_TO_TURN));
                navigationInfoSpec.setDistanceToTarget(intentCopy.getStringExtra(EXTRA_NAVIGATION_DISTANCE_TO_TARGET));
                // Prefer the time to destination value sent by the receiver, if present, as the ETA has been converted internally
                // in this case
                final int timeToDest = intentCopy.getIntExtra(EXTRA_NAVIGATION_TIME_TO_DESTINATION, 0);
                if( timeToDest != 0) {
                    navigationInfoSpec.setTotalTimeToDestination(timeToDest);
                } else {
                    navigationInfoSpec.setETA(intentCopy.getStringExtra(EXTRA_NAVIGATION_ETA));
                }
                navigationInfoSpec.setCompletionPercent(intentCopy.getIntExtra(EXTRA_NAVIGATION_COMPLETION_PERCENT, 0));
                deviceSupport.onSetNavigationInfo(navigationInfoSpec);
                break;
            case ACTION_SETNAVIGATIONROUTE:
                final NavigationRouteSpec navigationRouteSpec = intentCopy.getParcelableExtra(EXTRA_NAVIGATION_ROUTE_SPEC);
                if (navigationRouteSpec != null) {
                    deviceSupport.onSetNavigationRoute(navigationRouteSpec);
                }
                break;
            case ACTION_REQUEST_APPINFO:
                deviceSupport.onAppInfoReq();
                break;
            case ACTION_REQUEST_SCREENSHOT:
                deviceSupport.onScreenshotReq();
                break;
            case ACTION_STARTAPP: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                final boolean start = intentCopy.getBooleanExtra(EXTRA_APP_START, true);
                deviceSupport.onAppStart(uuid, start);
                break;
            }
            case ACTION_DOWNLOADAPP: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppDownload(uuid);
                break;
            }
            case ACTION_DELETEAPP: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppDelete(uuid);
                break;
            }
            case ACTION_APP_CONFIGURE: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                final String config = intentCopy.getStringExtra(EXTRA_APP_CONFIG);
                Integer id = null;
                if (intentCopy.hasExtra(EXTRA_APP_CONFIG_ID)) {
                    id = intentCopy.getIntExtra(EXTRA_APP_CONFIG_ID, 0);
                }
                deviceSupport.onAppConfiguration(uuid, config, id);
                break;
            }
            case ACTION_APP_CONFIG_REQUEST: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppConfigRequest(uuid);
                break;
            }
            case ACTION_APP_CONFIG_SET: {
                final UUID uuid = (UUID) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                final ArrayList<DynamicAppConfig> configs = intentCopy.getParcelableArrayListExtra(EXTRA_APP_CONFIG);
                deviceSupport.onAppConfigSet(uuid, configs);
                break;
            }
            case ACTION_APP_REORDER: {
                final UUID[] uuids = (UUID[]) intentCopy.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppReorder(uuids);
                break;
            }
            case ACTION_INSTALL: {
                final Uri uri = intentCopy.getParcelableExtra(EXTRA_URI);
                final Bundle options = Objects.requireNonNullElse(intentCopy.getBundleExtra(EXTRA_OPTIONS), Bundle.EMPTY);
                if (uri != null) {
                    LOG.info("will try to install app/fw");
                    deviceSupport.onInstallApp(uri, options);
                } else {
                    LOG.error("Got null uri for app to install");
                }
                break;
            }
            case ACTION_SET_ALARMS:
                final ArrayList<? extends Alarm> alarms = (ArrayList<? extends Alarm>) intentCopy.getSerializableExtra(EXTRA_ALARMS);
                deviceSupport.onSetAlarms(alarms);
                break;
            case ACTION_SET_REMINDERS:
                final ArrayList<? extends Reminder> reminders = (ArrayList<? extends Reminder>) intentCopy.getSerializableExtra(EXTRA_REMINDERS);
                deviceSupport.onSetReminders(reminders);
                break;
            case ACTION_SET_LOYALTY_CARDS:
                final ArrayList<LoyaltyCard> loyaltyCards = (ArrayList<LoyaltyCard>) intentCopy.getSerializableExtra(EXTRA_LOYALTY_CARDS);
                deviceSupport.onSetLoyaltyCards(loyaltyCards);
                break;
            case ACTION_SET_WORLD_CLOCKS:
                final ArrayList<? extends WorldClock> clocks = (ArrayList<? extends WorldClock>) intentCopy.getSerializableExtra(EXTRA_WORLD_CLOCKS);
                deviceSupport.onSetWorldClocks(clocks);
                break;
            case ACTION_SET_CONTACTS:
                final ArrayList<? extends Contact> contacts = (ArrayList<? extends Contact>) intentCopy.getSerializableExtra(EXTRA_CONTACTS);
                deviceSupport.onSetContacts(contacts);
                break;
            case ACTION_SYNC_WORKOUT_TEMPLATE:
                deviceSupport.onSyncWorkoutTemplate(intentCopy.getLongExtra(EXTRA_WORKOUT_TEMPLATE_ID, -1));
                break;
            case ACTION_ENABLE_REALTIME_STEPS: {
                final boolean enable = intentCopy.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableRealtimeSteps(enable);
                break;
            }
            case ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT: {
                final boolean enable = intentCopy.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableHeartRateSleepSupport(enable);
                break;
            }
            case ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL: {
                final int seconds = intentCopy.getIntExtra(EXTRA_INTERVAL_SECONDS, 0);
                deviceSupport.onSetHeartRateMeasurementInterval(seconds);
                break;
            }
            case ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT: {
                final boolean enable = intentCopy.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableRealtimeHeartRateMeasurement(enable);
                break;
            }
            case ACTION_SEND_CONFIGURATION: {
                final String config = intentCopy.getStringExtra(EXTRA_CONFIG);
                deviceSupport.onSendConfiguration(Objects.requireNonNull(config));
                break;
            }
            case ACTION_READ_CONFIGURATION: {
                final String config = intentCopy.getStringExtra(EXTRA_CONFIG);
                deviceSupport.onReadConfiguration(config);
                break;
            }
            case ACTION_TEST_NEW_FUNCTION: {
                final Bundle options = intentCopy.getBundleExtra(EXTRA_OPTIONS);
                deviceSupport.onTestNewFunction(options);
                break;
            }
            case ACTION_SEND_WEATHER: {
                deviceSupport.onSendWeather();
                break;
            }
            case ACTION_SET_LED_COLOR:
                final int color = intentCopy.getIntExtra(EXTRA_LED_COLOR, 0);
                deviceSupport.onSetLedColor(color);
                break;
            case ACTION_POWER_OFF:
                deviceSupport.onPowerOff();
                break;
            case ACTION_SET_FM_FREQUENCY:
                final float frequency = intentCopy.getFloatExtra(EXTRA_FM_FREQUENCY, -1);
                if (frequency != -1) {
                    deviceSupport.onSetFmFrequency(frequency);
                }
                break;
            case ACTION_SET_GPS_LOCATION:
                final Location location = intentCopy.getParcelableExtra(EXTRA_GPS_LOCATION);
                deviceSupport.onSetGpsLocation(location);
                break;
            case ACTION_SLEEP_AS_ANDROID:
                if (device.getDeviceCoordinator().supportsSleepAsAndroid(device) && GBApplication.getPrefs().getString(GBPrefs.SLEEP_AS_ANDROID_DEVICE, "").equals(device.getAddress())) {
                    final String sleepAsAndroidAction = intentCopy.getStringExtra(EXTRA_SLEEP_AS_ANDROID_ACTION);
                    deviceSupport.onSleepAsAndroidAction(sleepAsAndroidAction, intentCopy.getExtras());
                }
                break;
            case ACTION_CAMERA_STATUS_CHANGE:
                final GBDeviceEventCameraRemote.Event event = GBDeviceEventCameraRemote.intToEvent(intentCopy.getIntExtra(EXTRA_CAMERA_EVENT, -1));
                String filename = null;
                if (event == GBDeviceEventCameraRemote.Event.TAKE_PICTURE) {
                    filename = intentCopy.getStringExtra(EXTRA_CAMERA_FILENAME);
                }
                deviceSupport.onCameraStatusChange(event, filename);
                break;
            case ACTION_REQUEST_MUSIC_LIST:
                deviceSupport.onMusicListReq();
                break;
            case ACTION_REQUEST_MUSIC_OPERATION:
                final int operation = intentCopy.getIntExtra(EXTRA_REQUEST_MUSIC_OPERATION, -1);
                final int playlistIndex = intentCopy.getIntExtra(EXTRA_REQUEST_MUSIC_PLAY_LIST_INDEX, -1);
                final String playlistName = intentCopy.getStringExtra(EXTRA_REQUEST_MUSIC_PLAY_LIST_NAME);
                final ArrayList<Integer> musics = (ArrayList<Integer>) intentCopy.getSerializableExtra(EXTRA_REQUEST_MUSIC_MUSIC_IDS);
                deviceSupport.onMusicOperation(operation, playlistIndex, playlistName, musics);
                break;
        }
    }

}
