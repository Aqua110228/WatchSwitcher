/*  Copyright (C) 2015-2026 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Daniel Dakhno, Daniele Gobbetti, Davis Mosenkovs, Frank Slezak,
    Gabriele Monaco, Gordon Williams, ivanovlev, JohnnySun, José Rebelo, Julien
    Pivotto, Kasha, mvn23, Petr Vaněk, Sebastian Kranz, Steffen Liebergeld,
    Taavi Eomäe, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.model;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.devices.EventHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceCommunicationService;

/**
 *
 */
public interface DeviceService extends EventHandler {
    String PREFIX = "nodomain.freeyourgadget.gadgetbridge.devices";

    String ACTION_CONNECT = PREFIX + ".action.connect";
    String ACTION_NOTIFICATION = PREFIX + ".action.notification";
    String ACTION_DELETE_NOTIFICATION = PREFIX + ".action.delete_notification";
    String ACTION_CALLSTATE = PREFIX + ".action.callstate";
    String ACTION_SETCANNEDMESSAGES = PREFIX + ".action.setcannedmessages";
    String ACTION_SETTIME = PREFIX + ".action.settime";
    String ACTION_SETMUSICINFO = PREFIX + ".action.setmusicinfo";
    String ACTION_SETMUSICSTATE = PREFIX + ".action.setmusicstate";
    String ACTION_SET_PHONE_VOLUME = PREFIX + ".action.set_phone_volume";
    String ACTION_SET_PHONE_SILENT_MODE = PREFIX + ".action.set_phone_silent_mode";
    String ACTION_SETNAVIGATIONINFO = PREFIX + ".action.setnavigationinfo";
    String ACTION_SETNAVIGATIONROUTE = PREFIX + ".action.setnavigationroute";
    String ACTION_REQUEST_DEVICEINFO = PREFIX + ".action.request_deviceinfo";
    String ACTION_REQUEST_APPINFO = PREFIX + ".action.request_appinfo";
    String ACTION_REQUEST_SCREENSHOT = PREFIX + ".action.request_screenshot";
    String ACTION_STARTAPP = PREFIX + ".action.startapp";
    String ACTION_DOWNLOADAPP = PREFIX + ".action.downloadapp";
    String ACTION_DELETEAPP = PREFIX + ".action.deleteapp";
    String ACTION_APP_CONFIGURE = PREFIX + ".action.app_configure";
    String ACTION_APP_CONFIG_REQUEST = PREFIX + ".action.app_config_request";
    String ACTION_APP_CONFIG_SET = PREFIX + ".action.app_config_set";
    String ACTION_APP_REORDER = PREFIX + ".action.app_reorder";
    String ACTION_INSTALL = PREFIX + ".action.install";
    String ACTION_REBOOT = PREFIX + ".action.reboot";
    String ACTION_FACTORY_RESET = PREFIX + ".action.factory_reset";
    String ACTION_HEARTRATE_TEST = PREFIX + ".action.heartrate_test";
    String ACTION_FETCH_RECORDED_DATA = PREFIX + ".action.fetch_activity_data";
    String ACTION_DISCONNECT = PREFIX + ".action.disconnect";
    String ACTION_FIND_DEVICE = PREFIX + ".action.find_device";
    String ACTION_PHONE_FOUND = PREFIX + ".action.phone_found";
    String ACTION_SET_CONSTANT_VIBRATION = PREFIX + ".action.set_constant_vibration";
    String ACTION_SET_ALARMS = PREFIX + ".action.set_alarms";
    String ACTION_SAVE_ALARMS = PREFIX + ".action.save_alarms";
    String ACTION_SAVE_REMINDERS = PREFIX + ".action.save_reminders";
    String ACTION_SET_REMINDERS = PREFIX + ".action.set_reminders";
    String ACTION_SET_LOYALTY_CARDS = PREFIX + ".action.set_loyalty_cards";
    String ACTION_SET_WORLD_CLOCKS = PREFIX + ".action.set_world_clocks";
    String ACTION_SYNC_WORKOUT_TEMPLATE = PREFIX + ".action.sync_workout_template";
    String ACTION_SET_CONTACTS = PREFIX + ".action.set_contacts";
    String ACTION_ENABLE_REALTIME_STEPS = PREFIX + ".action.enable_realtime_steps";
    String ACTION_REALTIME_SAMPLES = PREFIX + ".action.realtime_samples";
    String ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT = PREFIX + ".action.realtime_hr_measurement";
    String ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT = PREFIX + ".action.enable_heartrate_sleep_support";
    String ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL = PREFIX + ".action.set_heartrate_measurement_intervarl";
    String ACTION_ADD_CALENDAREVENT = PREFIX + ".action.add_calendarevent";
    String ACTION_DELETE_CALENDAREVENT = PREFIX + ".action.delete_calendarevent";
    String ACTION_SEND_CONFIGURATION = PREFIX + ".action.send_configuration";
    String ACTION_READ_CONFIGURATION = PREFIX + ".action.read_configuration";
    String ACTION_SEND_WEATHER = PREFIX + ".action.send_weather";
    String ACTION_TEST_NEW_FUNCTION = PREFIX + ".action.test_new_function";
    String ACTION_SET_FM_FREQUENCY = PREFIX + ".action.set_fm_frequency";
    String ACTION_SET_GPS_LOCATION = PREFIX + ".action.set_gps_location";
    String ACTION_SET_LED_COLOR = PREFIX + ".action.set_led_color";
    String ACTION_POWER_OFF = PREFIX + ".action.power_off";
    String ACTION_CAMERA_STATUS_CHANGE = PREFIX + ".action.camera_status_change";
    String ACTION_REQUEST_MUSIC_LIST = PREFIX + ".action.request_music_list";
    String ACTION_REQUEST_MUSIC_OPERATION = PREFIX + ".action.request_music_operation";
    String EXTRA_REQUEST_MUSIC_OPERATION = "operation";
    String EXTRA_REQUEST_MUSIC_PLAY_LIST_INDEX = "playlistIndex";
    String EXTRA_REQUEST_MUSIC_PLAY_LIST_NAME = "playlistName";
    String EXTRA_REQUEST_MUSIC_MUSIC_IDS = "musicIds";

    String ACTION_SLEEP_AS_ANDROID = ".action.sleep_as_android";
    String EXTRA_SLEEP_AS_ANDROID_ACTION = "sleepasandroid_action";
    String EXTRA_NOTIFICATION_SPEC = "notification_spec";
    String EXTRA_NOTIFICATION_ID = "notification_id";
    String EXTRA_FIND_START = "find_start";
    String EXTRA_FIND_TARGET = "find_target";
    String EXTRA_VIBRATION_INTENSITY = "vibration_intensity";
    String EXTRA_CALL_SPEC = "call_spec";
    String EXTRA_CANNEDMESSAGES_SPEC = "cannedmessages_spec";
    String EXTRA_MUSIC_SPEC = "music_spec";
    String EXTRA_MUSIC_STATE_SPEC = "music_state_spec";
    String EXTRA_PHONE_VOLUME = "phone_volume";
    String EXTRA_PHONE_RINGER_MODE = "ringer_mode";
    String EXTRA_NAVIGATION_ROUTE_SPEC = "navigation_route_spec";
    String EXTRA_NAVIGATION_INSTRUCTION = "navigation_instruction";
    String EXTRA_NAVIGATION_DISTANCE_TO_TURN = "navigation_distance_to_turn";
    String EXTRA_NAVIGATION_DISTANCE_TO_TARGET = "navigation_distance_to_target";
    String EXTRA_NAVIGATION_NEXT_ACTION = "navigation_next_action";
    String EXTRA_NAVIGATION_ETA = "navigation_eta";
    String EXTRA_NAVIGATION_TIME_TO_DESTINATION = "navigation_time_to_destination";
    String EXTRA_NAVIGATION_COMPLETION_PERCENT = "navigation_completion_percent";
    String EXTRA_APP_UUID = "app_uuid";
    String EXTRA_APP_START = "app_start";
    String EXTRA_APP_CONFIG = "app_config";
    String EXTRA_APP_CONFIG_ID = "app_config_id";
    String EXTRA_URI = "uri";
    String EXTRA_OPTIONS = "options";
    String EXTRA_CONFIG = "config";
    String EXTRA_ALARMS = "alarms";
    String EXTRA_REMINDERS = "reminders";
    String EXTRA_LOYALTY_CARDS = "loyalty_cards";
    String EXTRA_WORLD_CLOCKS = "world_clocks";
    String EXTRA_WORKOUT_TEMPLATE_ID = "workout_template_id";
    String EXTRA_CONTACTS = "contacts";
    String EXTRA_CONNECT_FIRST_TIME = "connect_first_time";
    String EXTRA_BOOLEAN_ENABLE = "enable_realtime_steps";
    String EXTRA_INTERVAL_SECONDS = "interval_seconds";
    String EXTRA_RECORDED_DATA_TYPES = "data_types";
    String EXTRA_FM_FREQUENCY = "fm_frequency";
    String EXTRA_LED_COLOR = "led_color";
    String EXTRA_GPS_LOCATION = "gps_location";
    String EXTRA_CAMERA_EVENT = "event";
    String EXTRA_CAMERA_FILENAME = "filename";

    String EXTRA_REALTIME_SAMPLE = "realtime_sample";
    String EXTRA_TIMESTAMP = "timestamp";

    String EXTRA_CALENDAREVENT_SPEC = "calendarevent_spec";
    String EXTRA_CALENDAREVENT_ID = "calendarevent_id";
    String EXTRA_CALENDAREVENT_TYPE = "calendarevent_type";

    void connect();

    void connect(boolean firstTime);

    void disconnect();

    void quit();

    DeviceService forDevice(GBDevice device);

    /**
     * Requests information from the {@link DeviceCommunicationService} about the connection state,
     * firmware info, etc.
     * <p>
     * Note that this will not need a connection to the device -- only the cached information
     * from the service will be reported.
     * </p>
     */
    void requestDeviceInfo();

    @Nullable
    GBDevice getDevice();
}
