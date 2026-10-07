/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum DeviceSpecificSettingsScreen {
    ADVANCED_SETTINGS("pref_screen_advanced_settings", R.xml.devicesettings_root_advanced_settings, R.string.advanced_settings),
    ACTIVITY_INFO("pref_screen_activity_info", R.xml.devicesettings_root_activity_info, R.string.activity_info),
    AUDIO("pref_screen_audio", R.xml.devicesettings_root_audio, R.string.pref_header_audio),
    AUTHENTICATION("pref_screen_authentication", R.xml.devicesettings_root_authentication, R.string.pref_header_authentication),
    BATTERY("pref_screen_battery", R.xml.devicesettings_root_battery, R.string.battery),
    CALENDAR("pref_screen_calendar", R.xml.devicesettings_root_calendar, R.string.pref_header_calendar),
    CALLS_AND_NOTIFICATIONS("pref_screen_calls_and_notifications", R.xml.devicesettings_root_calls_and_notifications, R.string.pref_header_calls_and_notifications),
    CONNECTION("pref_screen_connection", R.xml.devicesettings_root_connection, R.string.pref_header_connection),
    INTERNET("pref_screen_internet", R.xml.devicesettings_root_internet, R.string.permission_internet_access_title),
    DASHBOARD("pref_screen_dashboard", R.xml.devicesettings_root_dashboard, R.string.pref_header_dashboard),
    DEVELOPER("pref_screen_developer", R.xml.devicesettings_root_developer, R.string.pref_title_developer_settings),
    DISPLAY("pref_screen_display", R.xml.devicesettings_root_display, R.string.pref_header_display),
    GENERIC("pref_screen_generic", R.xml.devicesettings_root_generic, R.string.pref_header_generic),
    LOCATION("pref_screen_location", R.xml.devicesettings_root_location, R.string.pref_header_location),
    NOTIFICATIONS("pref_screen_notifications", R.xml.devicesettings_root_notifications, R.string.pref_header_notifications),
    DATE_TIME("pref_screen_date_time", R.xml.devicesettings_root_date_time, R.string.dateformat_date_time),
    WORKOUT("pref_screen_workout", R.xml.devicesettings_root_workout, R.string.menuitem_workout),
    HEALTH("pref_screen_health", R.xml.devicesettings_root_health, R.string.pref_header_health),
    TOUCH_OPTIONS("pref_screen_touch_options", R.xml.devicesettings_root_touch_options, R.string.prefs_galaxy_touch_options),
    SOUND("pref_screen_sound", R.xml.devicesettings_root_sound, R.string.pref_header_sound),
    EXPERIMENTAL("pref_screen_experimental", R.xml.devicesettings_root_experimental, R.string.pref_experimental_settings_title),
    ;

    private final String key;
    @XmlRes
    private final int xml;
    @StringRes
    private final int title;

    DeviceSpecificSettingsScreen(final String key, final int xml, @StringRes final int title) {
        this.key = key;
        this.xml = xml;
        this.title = title;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public int getXml() {
        return xml;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    public static DeviceSpecificSettingsScreen fromXml(final int xml) {
        for (final DeviceSpecificSettingsScreen screen : DeviceSpecificSettingsScreen.values()) {
            if (screen.xml == xml) {
                return screen;
            }
        }

        return null;
    }

    @Nullable
    public static DeviceSpecificSettingsScreen fromKey(@NonNull final String key) {
        for (final DeviceSpecificSettingsScreen screen : DeviceSpecificSettingsScreen.values()) {
            if (screen.key.equals(key)) {
                return screen;
            }
        }

        return null;
    }
}
