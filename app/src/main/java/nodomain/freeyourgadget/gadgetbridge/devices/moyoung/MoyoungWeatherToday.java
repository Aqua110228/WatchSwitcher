/*  Copyright (C) 2019 krzys_h

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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import lineageos.weather.util.TemperatureUtils;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class MoyoungWeatherToday {
    public final byte conditionId;
    public final byte currentTemp;
    public final byte minTemp;
    public final byte maxTemp;
    public final Short pm25; // (*)
    public final String lunar_or_festival; // (*)
    public final String city; // (*)

    public MoyoungWeatherToday(WeatherSpec weatherSpec) {
        conditionId = MoyoungConstants.openWeatherConditionToMoyoungConditionId(weatherSpec.getCurrentConditionCode());
        final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();
        if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
            // Kelvin -> Fahrenheit
            currentTemp = (byte) TemperatureUtils.celsiusToFahrenheit(weatherSpec.getCurrentTemp() - 273);
            minTemp = (byte) TemperatureUtils.celsiusToFahrenheit(weatherSpec.getTodayMinTemp() - 273);
            maxTemp = (byte) TemperatureUtils.celsiusToFahrenheit(weatherSpec.getTodayMaxTemp() - 273);
        } else {
            // Kelvin -> Celsius
            currentTemp = (byte) (weatherSpec.getCurrentTemp() - 273);
            minTemp = (byte) (weatherSpec.getTodayMinTemp() - 273);
            maxTemp = (byte) (weatherSpec.getTodayMaxTemp() - 273);
        }
        pm25 = null;
        lunar_or_festival = StringUtils.pad("", 4);
        city = StringUtils.pad(StringUtils.truncate(weatherSpec.getLocation(), 4), 4);
    }
}
