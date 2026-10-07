/*  Copyright (C) 2016-2021 Andreas Shimokawa

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.pebble;

import android.annotation.SuppressLint;
import android.util.Pair;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventSendBytes;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;

public class AppMessageHandlerSimpleWeather extends AppMessageHandler {
    private static final int KEY_TEMPERATURE = 0;
    private static final int KEY_LOCATION = 1;
    private static final int KEY_ICON = 2;
    private static final int KEY_R_TIME12 = 3;
    private static final int KEY_R_TIME24 = 4;
    private static final int KEY_LANGUAGE = 5;
    private static final int KEY_DISCONNECTED_VIBES = 6;

    private static final int[] d09 = new int[]{300, 301, 302, 310, 311, 312, 313, 314, 321, 520, 521, 522, 531};
    private static final int[] d11 = new int[]{200, 201, 202, 210, 211, 212, 221, 230, 231, 231};
    private static final int[] d10 = new int[]{500, 501, 502, 503, 504};
    private static final int[] d13 = new int[]{511, 600, 601, 602, 611, 612, 613, 615, 616, 620, 621, 622};
    private static final int[] d50 = new int[]{701, 711, 721, 731, 741, 751, 761, 762, 771, 781};


    public AppMessageHandlerSimpleWeather(UUID uuid, PebbleProtocol pebbleProtocol) {
        super(uuid, pebbleProtocol);
    }

    public byte[] encodeWeatherMessage(WeatherSpec weatherSpec) {

        /*
            KEY_TEMPERATURE: Math.floor(json.main.temp) + (celsius ? "\u00B0C" : "\u00B0F"),
            KEY_LOCATION: json.name,
            KEY_ICON: json.weather[0].icon,
            KEY_R_TIME12: getTime(12),
            KEY_R_TIME24: getTime(24),
            KEY_LANGUAGE: options.language,
            KEY_DISCONNECTED_VIBES: "Y",
         */

        if (weatherSpec == null) {
            return null;
        }

        ArrayList<Pair<Integer, Object>> pairs = new ArrayList<>(7);
        pairs.add(new Pair<>(KEY_ICON, getIconForConditionCode(weatherSpec.getCurrentConditionCode(), !weatherSpec.isNight())));
        pairs.add(new Pair<>(KEY_TEMPERATURE, getCurrentTemperature(weatherSpec.getCurrentTemp())));
        pairs.add(new Pair<>(KEY_LOCATION, weatherSpec.getLocation()));
        //pairs.add(new Pair<>(KEY_LOCATION, "TEST"));
        pairs.add(new Pair<>(KEY_R_TIME12, getTime(false)));
        pairs.add(new Pair<>(KEY_R_TIME24, getTime(true)));
        pairs.add(new Pair<>(KEY_LANGUAGE, "EN"));
        pairs.add(new Pair<>(KEY_DISCONNECTED_VIBES, 'N'));
        byte[] weatherMessage = mPebbleProtocol.encodeApplicationMessagePush(PebbleProtocol.ENDPOINT_APPLICATIONMESSAGE, mUUID, pairs, null);

        ByteBuffer buf = ByteBuffer.allocate(weatherMessage.length);

        buf.put(weatherMessage);

        return buf.array();
    }

    private String getCurrentTemperature(int kelvin) {
        final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();

        if (temperatureUnit == TemperatureUnit.CELSIUS)
            return kelvin2celsius(kelvin) + "\u00B0C";
        else
            return kelvin2fahrenheit(kelvin) + "\u00B0F";
    }

    private int kelvin2celsius(int kelvin) {
        return kelvin - 273;
    }

    private long kelvin2fahrenheit(int kelvin) {
        return Math.round((9.0 / 5) * kelvin2celsius(kelvin) + 32);
    }

    private String getTime(Boolean h24) {
        @SuppressLint("SimpleDateFormat") DateFormat dateFormat = new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm");
        return dateFormat.format(new Date());
    }

    //Map ConditionCode to OWM Icon
    private String getIconForConditionCode(int conditionCode, final boolean isDay) {
        String icon = null;
        if (ArrayUtils.contains(d09, conditionCode)) {
            icon = "09";
        } else if (ArrayUtils.contains(d10, conditionCode)) {
            icon = "10";
        } else if (ArrayUtils.contains(d11, conditionCode)) {
            icon = "11";
        } else if (ArrayUtils.contains(d13, conditionCode)) {
            icon = "13";
        } else if (ArrayUtils.contains(d50, conditionCode)) {
            icon = "50";
        } else if (conditionCode == 800) {
            icon = "01";
        } else if (conditionCode == 801) {
            icon = "02";
        } else if (conditionCode == 802) {
            icon = "03";
        } else if (conditionCode == 803 || conditionCode == 804) {
            icon = "04";
        } else {
            icon = "01";
        }
        return icon + (isDay ? 'd' : 'n');
    }

    @Override
    public GBDeviceEvent[] onAppStart() {
        WeatherSpec weatherSpec = Weather.getWeatherSpec();
        if (weatherSpec == null) {
            return new GBDeviceEvent[]{null};
        }
        GBDeviceEventSendBytes sendBytes = new GBDeviceEventSendBytes();
        sendBytes.encodedBytes = encodeWeatherMessage(weatherSpec);
        return new GBDeviceEvent[]{sendBytes};
    }

    @Override
    public byte[] encodeUpdateWeather(WeatherSpec weatherSpec) {
        return encodeWeatherMessage(weatherSpec);
    }
}
